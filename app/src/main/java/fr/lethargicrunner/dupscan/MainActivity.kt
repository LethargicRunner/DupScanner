package fr.lethargicrunner.dupscan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        setContent { DupScanTheme { DupScanApp(this) } }
    }
}

@Composable
fun DupScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

data class StorageInfo(val label: String, val path: String)

/** Detecte toutes les memoires disponibles : interne + cartes SD eventuelles. */
fun detectStorages(): List<StorageInfo> {
    val list = mutableListOf<StorageInfo>()
    val internal = Environment.getExternalStorageDirectory().absolutePath
    list.add(StorageInfo("Memoire interne", internal))
    File("/storage").listFiles()
        ?.filter {
            it.isDirectory && it.canRead() &&
                it.name != "emulated" && it.name != "self"
        }
        ?.forEach { list.add(StorageInfo("Carte de stockage", it.absolutePath)) }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DupScanApp(activity: ComponentActivity) {
    val storages = remember { detectStorages() }
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply { storages.forEach { put(it.path, true) } }
    }
    val customRoots = remember { mutableStateListOf<String>() }
    var newRoot by remember { mutableStateOf("") }
    var extFilter by remember { mutableStateOf("mp3") }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val expandedGroups = remember { mutableStateOf(setOf(0)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("DupScan") }) },
    ) { innerPadding ->
        Surface(
            Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasAccess) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Acces aux fichiers requis", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "DupScan a besoin de l'acces a tous les fichiers pour scanner les memoires du telephone. Aucun fichier n'est jamais modifie ni supprime.",
                                fontSize = 13.sp,
                            )
                            Button(onClick = {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + activity.packageName),
                                    )
                                )
                            }) { Text("Accorder l'acces") }
                            OutlinedButton(onClick = { hasAccess = Environment.isExternalStorageManager() }) {
                                Text("J'ai accorde l'acces")
                            }
                        }
                    }
                } else {
                    // --- Selection des memoires ---
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Memoires a scanner", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                storages.forEach { s ->
                                    FilterChip(
                                        selected = selected[s.path] == true,
                                        onClick = { selected[s.path] = !(selected[s.path] ?: false) },
                                        label = {
                                            Column {
                                                Text(s.label, fontSize = 13.sp)
                                                Text(s.path, fontSize = 10.sp)
                                            }
                                        },
                                    )
                                }
                            }
                            if (customRoots.isNotEmpty()) {
                                Text("Dossiers personnalises :", fontSize = 13.sp)
                                customRoots.forEach { root ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(root, Modifier.fillMaxWidth(0.7f), fontSize = 12.sp)
                                        TextButton(onClick = { customRoots.remove(root) }) { Text("Retirer") }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newRoot,
                                    onValueChange = { newRoot = it },
                                    label = { Text("Chemin a ajouter (optionnel)") },
                                    modifier = Modifier.fillMaxWidth(0.7f),
                                )
                                Button(onClick = {
                                    if (newRoot.isNotBlank()) {
                                        customRoots.add(newRoot.trim())
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
                        }
                    }

                    val scanPaths = storages.filter { selected[it.path] == true }.map { it.path } + customRoots

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
                                        DupScanner.scan(scanPaths, exts.ifEmpty { null }) { p ->
                                            progress = p
                                        }
                                    }
                                    report = result
                                    scanning = false
                                    progress = null
                                }
                            }
                        },
                        enabled = !scanning && scanPaths.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (scanning) "Scan en cours…" else "Scanner les memoires selectionnees") }

                    progress?.let { p ->
                        Column {
                            if (p.total > 0) {
                                val frac = p.done.toFloat() / p.total
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    p.phase + " : " + p.done + " / " + p.total,
                                    fontSize = 12.sp,
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(p.phase + " : " + p.done + " fichiers…", fontSize = 12.sp)
                            }
                        }
                    }

                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }

                    report?.let { r ->
                        // --- Etat des lieux ---
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Etat des lieux", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Stat(r.groups.size.toString(), "groupes")
                                    Stat(r.redundant.toString(), "redondants")
                                    Stat(human(r.spaceGain), "recuperables")
                                }
                                Text(
                                    "sur " + human(r.totalBytes) + " scannes (" + r.scanned + " fichiers)",
                                    fontSize = 12.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val dir = activity.getExternalFilesDir(null)
                                        if (dir == null) {
                                            message = "Stockage externe indisponible."
                                        } else {
                                            val f = File(dir, "dupscan_rapport.csv")
                                            f.writeText(buildCsv(r))
                                            message = "Rapport ecrit : " + f.absolutePath
                                        }
                                    }) { Text("Exporter le rapport CSV") }
                                    TextButton(onClick = { expandedGroups.value = emptySet() }) {
                                        Text("Tout replier")
                                    }
                                }
                            }
                        }

                        LazyColumn {
                            itemsIndexed(r.groups) { i, g ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                        expandedGroups.value =
                                            if (i in expandedGroups.value) expandedGroups.value - i
                                            else expandedGroups.value + i
                                    },
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Groupe " + (i + 1) + " — " + g.files.size + " exemplaires",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            "Gain : " + human(g.gain),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (i in expandedGroups.value) {
                                            Spacer(Modifier.height(4.dp))
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
        }
    }
}

@Composable
fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
