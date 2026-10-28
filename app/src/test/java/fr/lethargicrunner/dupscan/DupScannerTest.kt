package fr.lethargicrunner.dupscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.random.Random
import org.junit.Test

class DupScannerTest {

    @Test
    fun detecteLesDoublonsExactsEtIgnoreLesFichiersDifferents() {
        val dir = createTempDir(prefix = "dupscan_test")
        try {
            val content = "le meme morceau".repeat(50)
            dir.resolve("a/morceau1.mp3").apply { parentFile.mkdirs() }.writeText(content)
            dir.resolve("b/copie_morceau.mp3").apply { parentFile.mkdirs() }.writeText(content)
            dir.resolve("c/autre_morceau.mp3").apply { parentFile.mkdirs() }
                .writeText(content + Random.nextInt().toString())

            val report = DupScanner.scan(listOf(dir.absolutePath), setOf("mp3"))

            assertEquals(3, report.scanned)
            assertEquals(1, report.groups.size)
            val group = report.groups.first()
            assertEquals(2, group.files.size)
            assertEquals(1, report.redundant)
            assertEquals(content.length.toLong(), group.gain)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun filtreParExtension() {
        val dir = createTempDir(prefix = "dupscan_test")
        try {
            dir.resolve("note.txt").writeText("identique")
            dir.resolve("copie.txt").writeText("identique")
            dir.resolve("x.mp3").writeText("identique")

            val reportAll = DupScanner.scan(listOf(dir.absolutePath), null)
            val reportMp3 = DupScanner.scan(listOf(dir.absolutePath), setOf("mp3"))

            assertEquals(1, reportAll.groups.size)
            assertTrue(reportMp3.groups.isEmpty())
            assertEquals(1, reportMp3.scanned)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun repertoireInexistantScanVide() {
        val report = DupScanner.scan(listOf("/chemin/qui/nexiste/pas"), null)
        assertEquals(0, report.scanned)
        assertTrue(report.groups.isEmpty())
    }
}
