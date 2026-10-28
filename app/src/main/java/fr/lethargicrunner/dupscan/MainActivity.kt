package fr.lethargicrunner.dupscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DupScanApp(this) }
    }
}

@Composable
fun DupScanApp(activity: ComponentActivity) {
    var roots by remember {
        mutableStateOf(
            listOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Download",
            )
        )
    }
    var newRoot by remember { mutableStateOf("") }
    var extFilter by remember { mutableStateOf("mp3") }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("DupScan", style = MaterialTheme.typography.headlineMedium)
                Text("Doublons exacts (SHA-256) — lecture seule stricte", fontSize = 12.sp)

                if (!hasAccess) {
                    Text("L'acces a tous les fichiers est requis pour scanner la bibliotheque.")
                    Button(onClick = {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + activity.packageName),
                            )
                        )
                    }) { Text("Accorder l'acces aux fichiers") }
                    TextButton(onClick = { hasAccess = Environment.isExternalStorageManager() }) {
                        Text("Actualiser l'etat des permissions")
                    }
                    return@Column
                }

                Text("Repertoires a scanner :", style = MaterialTheme.typography.titleMedium)
                roots.forEach { root ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(root, Modifier.fillMaxWidth(0.7f))
                        TextButton(onClick = { roots = roots - root }) { Text("Retirer") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newRoot,
                        onValueChange = { newRoot = it },
                        label = { Text("Chemin a ajouter") },
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    Button(onClick = {
                        if (newRoot.isNotBlank()) {
                            roots = roots + newRoot.trim()
                            newRoot = ""
                        }
                    }) { Text("Ajouter") }
                }
                OutlinedTextField(
                    value = extFilter,
                    onValueChange = { extFilter = it },
                    label = { Text("Extensions (ex : mp3,m4a — vide = tous fichiers)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (!scanning) {
                            scanning = true
                            report = null
                            message = null
                            scope.launch {
                                val exts = extFilter.split(",")
                                    .map { it.trim().lowercase() }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                val result = withContext(Dispatchers.IO) {
                                    DupScanner.scan(roots, exts.ifEmpty { null }) { p ->
                                        progress = p
                                    }
                                }
                                report = result
                                scanning = false
                                progress = null
                            }
                        }
                    },
                    enabled = !scanning && roots.isNotEmpty(),
                ) { Text(if (scanning) "Scan en cours…" else "Scanner") }

                progress?.let { p ->
                    Text(p.phase + " : " + p.done + "/" + p.total, fontSize = 12.sp)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                report?.let { r ->
                    Text(
                        r.groups.size.toString() + " groupe(s) — " + r.redundant + " fichier(s) redondant(s)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Espace recuperable : " + human(r.spaceGain) + " sur " + human(r.totalBytes) + " scans",
                    )
                    TextButton(onClick = {
                        val dir = activity.getExternalFilesDir(null)
                        if (dir == null) {
                            message = "Stockage externe indisponible."
                        } else {
                            val f = File(dir, "dupscan_rapport.csv")
                            f.writeText(buildCsv(r))
                            message = "Rapport ecrit : " + f.absolutePath
                        }
                    }) { Text("Exporter le rapport CSV") }
                    Divider()
                    LazyColumn {
                        items(r.groups.size) { i ->
                            val g = r.groups[i]
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(
                                    "Groupe " + (i + 1) + " — " + g.files.size + " exemplaires — gain " + human(g.gain),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                g.files.sortedBy { it.mtime }.forEach { f ->
                                    val mark = if (f == g.oldest) "  [le plus ancien]" else ""
                                    Text("• " + human(f.size) + "  " + f.path + mark, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun human(n: Long): String {
    var x = n.toDouble()
    for (unit in listOf("o", "Ko", "Mo", "Go", "To")) {
        if (x < 1024 || unit == "To") {
            return if (unit == "o") n.toString() + " o" else String.format(Locale.FRANCE, "%.1f %s", x, unit)
        }
        x /= 1024
    }
    return n.toString() + " o"
}

fun buildCsv(r: ScanReport): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
    val sb = StringBuilder("groupe,taille_octets,sha256,mtime_iso,chemin\n")
    r.groups.forEachIndexed { i, g ->
        g.files.forEach { f ->
            sb.append(i + 1).append(',')
                .append(f.size).append(',')
                .append(f.digest ?: "").append(',')
                .append(fmt.format(Date(f.mtime))).append(',')
                .append('"').append(f.path.replace("\"", "\"\"")).append('"')
                .append('\n')
        }
    }
    return sb.toString()
}
