package fr.lethargicrunner.dupscan

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashItem(val trashPath: String, val originalPath: String, val size: Long)

/**
 * Corbeille maison : les fichiers supprimes sont deplaces dans un dossier
 * .DupScannerTrash a la racine du meme volume (deplacement instantane,
 * aucune copie entre memoires), avec un index JSON pour la restauration.
 */
object Trash {

    private const val INDEX_FILE = "trash_index.json"

    private fun indexFile(ctx: Context): File = File(ctx.filesDir, INDEX_FILE)

    private fun volumeRoot(path: String): String {
        val parts = path.split('/')
        return if (parts.size >= 4 && parts[1] == "storage") {
            "/" + parts[2] + "/" + parts[3]
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
    }

    fun load(ctx: Context): MutableList<TrashItem> {
        return try {
            val f = indexFile(ctx)
            if (!f.exists()) return mutableListOf()
            val arr = JSONArray(f.readText())
            val items = mutableListOf<TrashItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(TrashItem(o.getString("trash"), o.getString("original"), o.getLong("size")))
            }
            items
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun count(ctx: Context): Int = load(ctx).size

    private fun save(ctx: Context, items: List<TrashItem>) {
        try {
            val arr = JSONArray()
            items.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("trash", t.trashPath)
                        .put("original", t.originalPath)
                        .put("size", t.size),
                )
            }
            indexFile(ctx).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun removeFromIndex(ctx: Context, trashPath: String) {
        val items = load(ctx)
        items.removeAll { it.trashPath == trashPath }
        save(ctx, items)
    }

    /** Deplace un fichier vers la corbeille de son volume. Retourne null si echec. */
    fun move(ctx: Context, path: String): TrashItem? {
        return try {
            val src = File(path)
            if (!src.isFile) return null
            val root = File(volumeRoot(path), ".DupScannerTrash")
            if (!root.exists() && !root.mkdirs()) return null
            var dst = File(root, src.name)
            var n = 1
            while (dst.exists()) {
                dst = File(root, src.name + "." + n)
                n++
            }
            val ok = if (src.renameTo(dst)) true else copyThenDelete(src, dst)
            if (!ok) return null
            val item = TrashItem(dst.absolutePath, src.absolutePath, src.length())
            val items = load(ctx)
            items.add(item)
            save(ctx, items)
            item
        } catch (_: Exception) {
            null
        }
    }

    private fun copyThenDelete(src: File, dst: File): Boolean {
        return try {
            src.copyTo(dst, overwrite = false)
            src.delete()
        } catch (_: Exception) {
            dst.delete()
            false
        }
    }

    /** Restaure un fichier a son emplacement d'origine (suffixe si l'emplacement est deja pris). */
    fun restore(ctx: Context, item: TrashItem): Boolean {
        return try {
            val src = File(item.trashPath)
            if (!src.exists()) {
                removeFromIndex(ctx, item.trashPath)
                return false
            }
            val original = File(item.originalPath)
            var dst = original
            var n = 1
            while (dst.exists()) {
                val base = original.nameWithoutExtension
                val ext = original.extension
                dst = File(original.parentFile, base + " (" + n + ")" + if (ext.isEmpty()) "" else "." + ext)
                n++
            }
            dst.parentFile?.mkdirs()
            if (!src.renameTo(dst)) return false
            removeFromIndex(ctx, item.trashPath)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Suppression definitive d'un element de la corbeille. */
    fun deleteForever(ctx: Context, item: TrashItem): Boolean {
        val f = File(item.trashPath)
        val ok = !f.exists() || f.delete()
        removeFromIndex(ctx, item.trashPath)
        return ok
    }

    /** Vide toute la corbeille. Retourne le nombre de fichiers definitivement supprimes. */
    fun empty(ctx: Context): Int {
        var deleted = 0
        load(ctx).forEach { item ->
            val f = File(item.trashPath)
            if (!f.exists() || f.delete()) deleted++
            removeFromIndex(ctx, item.trashPath)
        }
        return deleted
    }
}
