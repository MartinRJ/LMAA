package de.lmaa.app

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun customStyleOwnsStructureAndOutputFormat() = runBlocking {
        val output = "Ergebnis: genau ein freier Satz ohne Überschrift."
        val generator = FakeBriefingGenerator(mutableListOf(output))
        val style = """Antworte in genau einem Satz.

Nutze keine Überschriften und kein Markdown."""

        val result = BriefingService(generator).create(
            transcript = transcript(),
            metadata = metadata(),
            canonicalUrl = "https://www.youtube.com/watch?v=ABCDEFGHIJK",
            styleName = "Freier Stil",
            styleInstructions = style,
        ) as BriefingGenerationResult.Success

        assertEquals(output, result.document.markdown)
        val instructions = generator.calls.single().first
        assertTrue(instructions.contains(style))
        DEFAULT_BRIEFING_HEADINGS.forEach { heading ->
            assertFalse(instructions.contains(heading))
        }
        assertFalse(instructions.contains("Fehlende Informationen"))
        assertFalse(instructions.contains("Verlinke Zeitmarken"))
    }

    @Test
    fun defaultStyleContainsFormerStructureAndEditorialRules() {
        DEFAULT_BRIEFING_HEADINGS.forEach { heading ->
            assertTrue(DEFAULT_STYLE_INSTRUCTIONS.contains(heading))
        }
        assertTrue(DEFAULT_STYLE_INSTRUCTIONS.contains("fehlende Belege"))
        assertTrue(DEFAULT_STYLE_INSTRUCTIONS.contains("Unsicherheiten"))
        assertTrue(DEFAULT_STYLE_INSTRUCTIONS.contains("Zeitmarken"))
        assertTrue(DEFAULT_STYLE_INSTRUCTIONS.contains("Markdown"))
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
        assertTrue(
            generator.calls.last().second.contains(
                "TEILZUSAMMENFASSUNG $chunkCount/$chunkCount",
            ),
        )
        val mapInstructions = generator.calls.first().first
        assertTrue(mapInstructions.contains(DEFAULT_STYLE_INSTRUCTIONS))
        assertFalse(mapInstructions.substringBefore("Die folgende Stilkonfiguration").contains("Markdown"))
        assertFalse(mapInstructions.substringBefore("Die folgende Stilkonfiguration").contains("Zeitmarken"))
    }

    @Test
    fun emptyAndActiveMarkupOutputsRemainRejectedIndependentlyOfStyle() = runBlocking {
        val empty = BriefingService(FakeBriefingGenerator(mutableListOf("  "))).create(
            transcript(),
            metadata(),
            "https://www.youtube.com/watch?v=ABCDEFGHIJK",
            "Frei",
            "Beliebige Struktur.",
        )
        val unsafe = BriefingService(
            FakeBriefingGenerator(mutableListOf("<script>alert('x')</script>")),
        ).create(
            transcript(),
            metadata(),
            "https://www.youtube.com/watch?v=ABCDEFGHIJK",
            "Frei",
            "Beliebige Struktur.",
        )

        assertEquals(BriefingGenerationResult.Failure("EMPTY_BRIEFING"), empty)
        assertEquals(BriefingGenerationResult.Failure("UNSAFE_BRIEFING_MARKUP"), unsafe)
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

    private fun validMarkdown() = DEFAULT_BRIEFING_HEADINGS.joinToString("\n\n") { "$it\nTest" }
}
