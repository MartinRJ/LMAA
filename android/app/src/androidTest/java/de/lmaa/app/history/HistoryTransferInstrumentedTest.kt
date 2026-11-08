package de.lmaa.app.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lmaa.app.AnalysisStage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryTransferInstrumentedTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LmaaDatabase::class.java).build()
    private val fixture = """{"format":"lmaa-history","version":1,"styles":[],"briefings":[{
      "id":"portable-1","videoId":"abcdefghijk","title":"Synthetisch","channel":"Test",
      "markdown":"[12:34] Inhalt","ts":1,"model":"gpt-5.6-sol","styleName":"Kurz",
      "styleInstructions":"Ein Satz.","styleOutputLanguage":"Deutsch",
      "transcript":{"content":"Vollständige Quelle 😃","label":"TRANSKRIPT","language":"de","provider":"primary"},
      "messages":[{"id":"message-1","role":"user","content":"Warum?","ts":2}]}]}"""

    @Test fun portableRoundtripMergesMessagesAndRejectsConflictsAtomically() = runBlocking {
        database().use { a -> database().use { b ->
            val first = HistoryArchive(a); val second = HistoryArchive(b)
            assertEquals(1, first.import(fixture))
            val exported = first.export()
            assertEquals(1, second.import(exported))
            assertEquals(0, second.import(exported))
            val entry = b.briefingDao().exportBriefings().single()
            assertEquals("Warum?", b.chatDao().messages(entry.id).single().content)
            val conflict = JSONObject(exported)
            conflict.getJSONArray("briefings").getJSONObject(0).put("markdown", "Widerspruch")
            try { second.import(conflict.toString()); fail("Conflict must reject") } catch (_: IllegalArgumentException) {}
            assertEquals("[12:34] Inhalt", b.briefingDao().exportBriefings().single().markdown)
            assertEquals(0, first.import(second.export()))
            b.briefingDao().deleteBriefingWithOwnedData(entry.id, 3)
            assertTrue(b.chatDao().messages(entry.id).isEmpty())
        } }
    }
    @Test fun pauseBoundaryAndColdStyleSelection() = runBlocking {
        database().use { db ->
            val styles = BriefingStyleRepository(db.briefingStyleDao())
            styles.ensureDefault()
            val custom = styles.create("Kurz", "Ein Satz.", "Deutsch")
            styles.setActive(custom.id)
            assertEquals(custom.id, styles.selected().id)
            val jobs = AnalysisJobRepository(db.analysisJobDao())
            val early = jobs.create("https://www.youtube.com/watch?v=abcdefghijk", custom.snapshot())
            assertTrue(jobs.pauseBeforeGeneration(early.jobId))
            assertFalse(jobs.markRunning(early.jobId, AnalysisStage.BRIEFING))
            val late = jobs.create("https://www.youtube.com/watch?v=abcdefghijk", custom.snapshot())
            assertTrue(jobs.markRunning(late.jobId, AnalysisStage.BRIEFING))
            jobs.keepInBackground(late.jobId)
            assertTrue(jobs.find(late.jobId)?.isBackground == true)
            assertFalse(jobs.pauseBeforeGeneration(late.jobId))
            assertEquals(AnalysisJobStatus.RUNNING, jobs.find(late.jobId)?.status)
        }
    }
}

internal inline fun <T> LmaaDatabase.use(block: (LmaaDatabase) -> T): T =
    try { block(this) } finally { close() }
