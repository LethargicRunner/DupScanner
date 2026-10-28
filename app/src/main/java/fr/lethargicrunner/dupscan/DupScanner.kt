package fr.lethargicrunner.dupscan

import java.io.File
import java.security.MessageDigest

data class FileInfo(val path: String, val size: Long, val mtime: Long) {
    var digest: String? = null
}

data class DupGroup(val files: List<FileInfo>) {
    val size: Long get() = files.first().size
    val gain: Long get() = size * (files.size - 1)
    val oldest: FileInfo get() = files.minBy { it.mtime }
}

data class ScanReport(
    val scanned: Int,
    val totalBytes: Long,
    val groups: List<DupGroup>,
) {
    val redundant: Int get() = groups.sumOf { it.files.size - 1 }
    val spaceGain: Long get() = groups.sumOf { it.gain }
}

data class ScanProgress(val phase: String, val done: Int, val total: Int)

/**
 * Detecteur de doublons exacts, en lecture seule stricte.
 *
 * Methode :
 *  1. inventaire des fichiers (taille, chemin) ;
 *  2. regroupement par taille (des tailles differentes ne peuvent pas etre
 *     des doublons — evite ~99 % des lectures) ;
 *  3. hash SHA-256 uniquement des fichiers dont la taille est partagee ;
 *  4. groupes tries par espace recuperable decroissant.
 *
 * total < 0 dans ScanProgress signifie "en cours, total inconnu" (inventaire).
 */
object DupScanner {

    private const val CHUNK = 1024 * 1024

    fun scan(
        roots: List<String>,
        extensions: Set<String>? = null,
        minSize: Long = 1,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanReport {
        // Phase 1 : inventaire
        val files = ArrayList<FileInfo>()
        var count = 0
        for (root in roots) {
            val dir = File(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .onFail { _, _ -> } // repertoires illisibles (ex : Android/data) : on ignore
                .forEach { f ->
                    try {
                        if (!f.isFile) return@forEach
                        if (extensions != null &&
                            f.extension.lowercase() !in extensions
                        ) return@forEach
                        val size = f.length()
                        if (size < minSize) return@forEach
                        files.add(FileInfo(f.absolutePath, size, f.lastModified()))
                        if (++count % 500 == 0) {
                            onProgress(ScanProgress("inventaire", count, -1))
                        }
                    } catch (_: SecurityException) {
                    }
                }
        }
        val totalBytes = files.sumOf { it.size }
        onProgress(ScanProgress("inventaire", files.size, files.size))

        // Phase 2 : regroupement par taille
        val bySize = files.groupBy { it.size }.filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        onProgress(ScanProgress("candidats", 0, candidates.size))

        // Phase 3 : hash SHA-256 des candidats uniquement
        val byDigest = HashMap<Pair<Long, String>, MutableList<FileInfo>>()
        candidates.forEachIndexed { i, fi ->
            val digest = sha256(File(fi.path))
            if (digest != null) {
                fi.digest = digest
                byDigest.getOrPut(fi.size to digest) { mutableListOf() }.add(fi)
            }
            if ((i + 1) % 10 == 0 || i + 1 == candidates.size) {
                onProgress(ScanProgress("hachage", i + 1, candidates.size))
            }
        }

        val groups = byDigest.values
            .filter { it.size > 1 }
            .map { DupGroup(it) }
            .sortedByDescending { it.gain }

        return ScanReport(files.size, totalBytes, groups)
    }

    private fun sha256(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
