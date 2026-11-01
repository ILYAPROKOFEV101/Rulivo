package com.ilya.rulivo

import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.ar.DiagnosticJournal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AccuracyDiagnosticsTest {
    @Test fun privateJournalRoundTripsWithAndroidJsonAndDoesNotRecordImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.noBackupFilesDir, "accuracy-test-${UUID.randomUUID()}")
        try {
            val journal = DiagnosticJournal(folder, mapOf("model" to "тест", "images_recorded" to false))
            journal.record("frame", mapOf("frame_timestamp_ns" to 9_000_000_000_000_001L,
                "tracking" to false, "distance_m" to Double.NaN, "message" to "a\n\"\\😀"))
            journal.close()
            assertTrue(journal.awaitClosed(5000))
            val events = folder.listFiles()!!.single().readLines().map(::JSONObject)
            assertEquals(3, events.size)
            assertFalse(events[0].getBoolean("images_recorded"))
            assertEquals(9_000_000_000_000_001L, events[1].getLong("frame_timestamp_ns"))
            assertTrue(events[1].isNull("distance_m"))
            assertEquals("a\n\"\\😀", events[1].getString("message"))
            assertEquals("closed", events[2].getString("reason"))
        } finally {
            folder.deleteRecursively()
        }
    }
}
