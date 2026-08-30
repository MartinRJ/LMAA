package de.lmaa.app

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingServiceTest {
    @Test
    fun singleChunkProducesValidatedBriefing() = runBlocking {
        val generator = FakeBriefingGenerator(mutableListOf(validMarkdown()))
        val result = BriefingService(generator).create(
            transcript = transcript(),
            metadata = metadata(),
            canonicalUrl = "https://www.youtube.com/watch?v=ABCDEFGHIJK",
        ) as BriefingGenerationResult.Success

        assertEquals("gpt-5.6-sol", result.document.model)
        assertEquals(1, result.document.mapChunkCount)
        assertTrue(generator.calls.single().second.contains("UNTRUSTED_TRANSKRIPT"))
        assertTrue(generator.calls.single().second.contains("UNTRUSTED_OEMBED_METADATEN"))
        assertTrue(generator.calls.single().first.contains("keine Tools"))
    }

    @Test
    fun longTranscriptUsesChronologicalMapReduce() = runBlocking {
        val segments = List(6) { index ->
            TranscriptSegment("Segment $index " + "x".repeat(600), index.toDouble(), 1.0)
        }
        val chunkCount = chunkTranscript(segments, 1_000).size
        val generator = FakeBriefingGenerator(
            (List(chunkCount) { "Teilzusammenfassung" } + validMarkdown()).toMutableList(),
        )

        val result = BriefingService(generator, 1_000).create(
            transcript().copy(segments = segments),
            metadata(),
            "https://www.youtube.com/watch?v=ABCDEFGHIJK",
        ) as BriefingGenerationResult.Success

        assertTrue(chunkCount > 1)
        assertEquals(chunkCount, result.document.mapChunkCount)
        assertTrue(generator.calls.last().second.contains("Teil $chunkCount/$chunkCount"))
    }

    @Test
    fun rapidApiRawResponseIsPassedUnchangedAsUntrustedData() = runBlocking {
        val raw = """{"items":[{"text":"Zeile 1\\nZeile 2","start":1.25}],"opaque":true}"""
        val generator = FakeBriefingGenerator(mutableListOf(validMarkdown()))

        val result = BriefingService(generator).create(
            transcript = transcript().copy(
                provider = "rapidapi:custom",
                segments = emptyList(),
                rawContent = raw,
            ),
            metadata = metadata(),
            canonicalUrl = "https://www.youtube.com/watch?v=ABCDEFGHIJK",
        )

        assertTrue(result is BriefingGenerationResult.Success)
        val input = generator.calls.single().second
        assertTrue(input.contains("BEGIN UNTRUSTED_RAPIDAPI_RAW_RESPONSE"))
        assertTrue(input.contains(raw))
        assertEquals(raw, input.substringAfter("BEGIN UNTRUSTED_RAPIDAPI_RAW_RESPONSE ---\n")
            .substringBefore("\n--- END UNTRUSTED_RAPIDAPI_RAW_RESPONSE"))
    }

    @Test
    fun rawChunkingPreservesUnicodeExactly() {
        val raw = "a😀b😀c"
        val chunks = chunkRawResponse(raw, 2)

        assertEquals(raw, chunks.joinToString(""))
        assertTrue(chunks.none { chunk ->
            chunk.firstOrNull()?.isLowSurrogate() == true ||
                chunk.lastOrNull()?.isHighSurrogate() == true
        })
    }

    private class FakeBriefingGenerator(
        private val responses: MutableList<String>,
    ) : BriefingTextGenerator {
        override val model = "gpt-5.6-sol"
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun generate(
            instructions: String,
            input: String,
            maxOutputTokens: Int,
        ): TextGenerationResult {
            calls += instructions to input
            return TextGenerationResult.Success(responses.removeAt(0))
        }
    }

    private fun transcript() = TranscriptDocument(
        "ABCDEFGHIJK",
        "de",
        false,
        "primary",
        listOf(TranscriptSegment("Synthetischer Inhalt.", 0.0, 2.0)),
    )

    private fun metadata() = VideoMetadata(
        "ABCDEFGHIJK",
        "Testvideo",
        null,
        "Testkanal",
        null,
        null,
        null,
        "https://i.ytimg.com/vi/ABCDEFGHIJK/hqdefault.jpg",
        Instant.EPOCH,
    )

    private fun validMarkdown() = REQUIRED_BRIEFING_HEADINGS.joinToString("\n\n") { "$it\nTest" }
}
