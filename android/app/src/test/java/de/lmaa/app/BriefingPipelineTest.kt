package de.lmaa.app

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingPipelineTest {
    @Test
    fun `one action executes validation transcript metadata and briefing in order`() = runBlocking {
        val calls = mutableListOf<String>()
        val pipeline = BriefingPipeline(
            transcriptProvider = transcriptProvider(calls),
            metadataProvider = metadataProvider(calls),
            briefingCreator = briefingCreator(calls),
        )
        val stages = mutableListOf<AnalysisStage>()

        val result = pipeline.analyze("https://youtube.com/shorts/Rq5iOD-mcEI") {
            stages += it
        }

        assertTrue(result is AnalysisResult.Success)
        assertEquals(listOf("transcript", "metadata", "briefing"), calls)
        assertEquals(
            listOf(AnalysisStage.TRANSCRIPT, AnalysisStage.METADATA, AnalysisStage.BRIEFING),
            stages,
        )
        val analysis = (result as AnalysisResult.Success).analysis
        assertEquals("https://www.youtube.com/watch?v=Rq5iOD-mcEI", analysis.canonicalUrl)
        assertEquals("Testvideo", analysis.metadata.title)
        assertEquals("# Kernaussage\nInhalt", analysis.briefing.markdown)
    }

    @Test
    fun `invalid link stops before providers`() = runBlocking {
        val calls = mutableListOf<String>()
        val pipeline = BriefingPipeline(
            transcriptProvider = transcriptProvider(calls),
            metadataProvider = metadataProvider(calls),
            briefingCreator = briefingCreator(calls),
        )

        val result = pipeline.analyze("https://example.invalid/video")

        assertEquals(AnalysisResult.Failure("URL_INVALID"), result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `transcript failure stops before external briefing requests`() = runBlocking {
        val calls = mutableListOf<String>()
        val pipeline = BriefingPipeline(
            transcriptProvider = object : TranscriptProvider {
                override suspend fun fetch(
                    videoId: String,
                    preferredLanguages: List<String>,
                ): TranscriptFetchResult {
                    calls += "transcript"
                    return TranscriptFetchResult.Failure("TRANSCRIPTS_DISABLED")
                }
            },
            metadataProvider = metadataProvider(calls),
            briefingCreator = briefingCreator(calls),
        )

        val result = pipeline.analyze("https://youtu.be/Rq5iOD-mcEI")

        assertEquals(AnalysisResult.Failure("TRANSCRIPTS_DISABLED"), result)
        assertEquals(listOf("transcript"), calls)
    }

    private fun transcriptProvider(calls: MutableList<String>) = object : TranscriptProvider {
        override suspend fun fetch(
            videoId: String,
            preferredLanguages: List<String>,
        ): TranscriptFetchResult {
            calls += "transcript"
            return TranscriptFetchResult.Success(transcript(videoId))
        }
    }

    private fun metadataProvider(calls: MutableList<String>) = object : MetadataProvider {
        override suspend fun fetch(videoId: String): MetadataFetchResult {
            calls += "metadata"
            return MetadataFetchResult.Success(metadata(videoId))
        }
    }

    private fun briefingCreator(calls: MutableList<String>) = object : BriefingCreator {
        override suspend fun create(
            transcript: TranscriptDocument,
            metadata: VideoMetadata,
            canonicalUrl: String,
            styleName: String,
            styleInstructions: String,
        ): BriefingGenerationResult {
            calls += "briefing"
            return BriefingGenerationResult.Success(
                BriefingDocument("# Kernaussage\nInhalt", OpenAiResponsesClient.MODEL, 1),
            )
        }
    }

    private fun transcript(videoId: String) = TranscriptDocument(
        videoId = videoId,
        languageCode = "de",
        isGenerated = true,
        provider = "primary",
        segments = listOf(TranscriptSegment("Inhalt", 0.0, 1.0)),
    )

    private fun metadata(videoId: String) = VideoMetadata(
        videoId = videoId,
        title = "Testvideo",
        channelId = null,
        channelTitle = "Testkanal",
        publishedAt = null,
        durationIso8601 = null,
        durationSeconds = null,
        thumbnailUrl = "https://example.test/thumbnail.jpg",
        fetchedAt = Instant.EPOCH,
    )
}
