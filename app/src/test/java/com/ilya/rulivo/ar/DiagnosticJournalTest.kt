package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun jsonEscapesUntrustedTextAndNonFiniteSensorValues() {
        assertEquals("{\"message\":\"a\\n\\r\\t\\\"\\\\\\u0001\",\"positions\":[1.0,null,null,true]}",
            DiagnosticJson.encode(mapOf("message" to "a\n\r\t\"\\\u0001",
                "positions" to listOf(1.0, Double.NaN, Double.POSITIVE_INFINITY, true))))
        assertEquals("\"\\ud83d\\ude00\"", DiagnosticJson.encode("😀"))
    }

    @Test fun closeDrainsAcceptedRecordsAndRejectsFurtherWrites() {
        val folder = temporary.newFolder()
        val journal = DiagnosticJournal(folder, mapOf("model" to "device"))
        assertTrue(journal.record("frame", mapOf("point" to listOf(1, 2, 3))))
        journal.close()
        assertFalse(journal.record("too_late"))
        assertTrue(journal.awaitClosed(5000))
        val lines = folder.listFiles()!!.single().readLines()
        assertEquals(3, lines.size)
        assertTrue(lines.first().contains("\"event\":\"session\""))
        assertTrue(lines[1].contains("\"point\":[1,2,3]"))
        assertTrue(lines.last().contains("\"dropped_events\":0"))
        assertTrue(lines.all { it.contains(journal.sessionId) })
    }

    @Test fun unavailableDiskDisablesLoggingWithoutThrowingOnProducer() {
        var failure: Exception? = null
        val journal = DiagnosticJournal(temporary.newFile(), emptyMap(), onFailure = { failure = it })
        assertTrue(journal.awaitClosed(5000))
        assertNotNull(failure)
        assertFalse(journal.record("frame"))
        journal.close()
    }

    @Test fun rotationRetainsOnlyConfiguredSessionLogsAndPreservesUnrelatedFiles() {
        val folder = temporary.newFolder()
        File(folder, "notes.txt").writeText("keep")
        repeat(4) { index -> File(folder, "accuracy-old-$index.jsonl").apply {
            writeText("{}"); setLastModified(1000L + index)
        } }
        val journal = DiagnosticJournal(folder, emptyMap(), retainedSessions = 3)
        journal.close()
        assertTrue(journal.awaitClosed(5000))
        assertEquals(3, folder.listFiles()!!.count { it.extension == "jsonl" })
        assertTrue(File(folder, "accuracy-old-3.jsonl").exists())
        assertFalse(File(folder, "accuracy-old-0.jsonl").exists())
        assertTrue(File(folder, "notes.txt").exists())
    }

    @Test fun sizeLimitWritesExplicitTerminalMarker() {
        val folder = temporary.newFolder()
        val journal = DiagnosticJournal(folder, emptyMap(), maxBytes = 1024)
        journal.record("frame", mapOf("payload" to "x".repeat(2000)))
        assertTrue(journal.awaitClosed(5000))
        assertFalse(journal.record("frame"))
        val text = folder.listFiles()!!.single().readText()
        assertTrue(text.contains("\"reason\":\"size_limit\""))
        assertFalse(text.contains("payload"))
    }

    @Test fun queueLossIsReportedAndAcceptedRecordsAreDrained() {
        val folder = temporary.newFolder()
        val journal = DiagnosticJournal(folder, emptyMap(), queueCapacity = 1)
        val accepted = (1..1000).count { journal.record("frame", mapOf("value" to it)) }
        journal.close()
        assertTrue(journal.awaitClosed(5000))
        val lines = folder.listFiles()!!.single().readLines()
        assertEquals(accepted + 2, lines.size)
        assertTrue(lines.last().contains("\"dropped_events\":${1000 - accepted}"))
    }
}
