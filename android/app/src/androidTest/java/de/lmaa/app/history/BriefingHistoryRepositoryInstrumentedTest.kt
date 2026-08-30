package de.lmaa.app.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.lmaa.app.BriefingDocument
import de.lmaa.app.CompletedAnalysis
import de.lmaa.app.OpenAiResponsesClient
import de.lmaa.app.TranscriptDocument
import de.lmaa.app.TranscriptSegment
import de.lmaa.app.VideoMetadata
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BriefingHistoryRepositoryInstrumentedTest {
    private lateinit var context: Context
    private val databaseName = "lmaa-history-instrumented-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun history_isImmutableAndReadableAfterDatabaseReopen() = runBlocking {
        var database = openDatabase()
        var repository = BriefingHistoryRepository(database.briefingDao())

        val first = repository.save(analysis("Erstes Briefing"))
        val second = repository.save(analysis("Zweites Briefing"))

        assertNotEquals(first.briefingId, second.briefingId)
        assertEquals(2, repository.history.first().size)
        assertEquals("Erstes Briefing", repository.find(first.briefingId)?.markdown)
        assertEquals("Zweites Briefing", repository.find(second.briefingId)?.markdown)

        database.close()
        database = openDatabase()
        repository = BriefingHistoryRepository(database.briefingDao())

        val restored = repository.find(first.briefingId)
        assertNotNull(restored)
        assertEquals("Erstes Briefing", restored?.markdown)
        assertEquals("Testvideo", restored?.title)
        assertEquals("https://www.youtube.com/watch?v=Rq5iOD-mcEI", restored?.canonicalUrl)
        database.close()
    }

    private fun openDatabase(): LmaaDatabase = Room.databaseBuilder(
        context,
        LmaaDatabase::class.java,
        databaseName,
    ).build()

    private fun analysis(markdown: String) = CompletedAnalysis(
        canonicalUrl = "https://www.youtube.com/watch?v=Rq5iOD-mcEI",
        transcript = TranscriptDocument(
            videoId = "Rq5iOD-mcEI",
            languageCode = "en",
            isGenerated = true,
            provider = "primary",
            segments = listOf(TranscriptSegment("Testinhalt", 0.0, 1.0)),
        ),
        metadata = VideoMetadata(
            videoId = "Rq5iOD-mcEI",
            title = "Testvideo",
            channelId = null,
            channelTitle = "Testkanal",
            publishedAt = null,
            durationIso8601 = null,
            durationSeconds = null,
            thumbnailUrl = "https://example.test/thumbnail.jpg",
            fetchedAt = Instant.EPOCH,
        ),
        briefing = BriefingDocument(
            markdown = markdown,
            model = OpenAiResponsesClient.MODEL,
            mapChunkCount = 1,
        ),
    )
}
