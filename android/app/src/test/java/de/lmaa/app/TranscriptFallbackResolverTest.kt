package de.lmaa.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFallbackResolverTest {
    @Test
    fun primarySuccessNeverCallsFallback() = runBlocking {
        val primary = FakeTranscriptProvider(success("primary"))
        val fallback = FakeTranscriptProvider(success("rapidapi"))

        val result = TranscriptFallbackResolver(primary, fallback).fetch(
            "ABCDEFGHIJK",
            fallbackEnabled = true,
        ) as TranscriptFetchResult.Success

        assertEquals("primary", result.document.provider)
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun optOutNeverCallsFallbackForAllowedError() = runBlocking {
        val fallback = FakeTranscriptProvider(success("rapidapi"))

        val result = TranscriptFallbackResolver(
            FakeTranscriptProvider(failure("REQUEST_BLOCKED")),
            fallback,
        ).fetch("ABCDEFGHIJK", fallbackEnabled = false)

        assertEquals("REQUEST_BLOCKED", (result as TranscriptFetchResult.Failure).code)
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun allowedTechnicalErrorsCallFallbackExactlyOnce() = runBlocking {
        listOf("REQUEST_BLOCKED", "REQUEST_FAILED").forEach { code ->
            val fallback = FakeTranscriptProvider(success("rapidapi"))

            val result = TranscriptFallbackResolver(
                FakeTranscriptProvider(failure(code)),
                fallback,
            ).fetch("ABCDEFGHIJK", fallbackEnabled = true)

            assertEquals("rapidapi", (result as TranscriptFetchResult.Success).document.provider)
            assertEquals(1, fallback.callCount)
        }
    }

    @Test
    fun semanticAndApplicationErrorsNeverCallFallback() = runBlocking {
        val disallowed = listOf(
            "INVALID_VIDEO_ID",
            "TRANSCRIPTS_DISABLED",
            "NO_TRANSCRIPT",
            "VIDEO_UNAVAILABLE",
            "PYTHON_BRIDGE_ERROR",
            "INTERNAL_ERROR",
        )
        disallowed.forEach { code ->
            val fallback = FakeTranscriptProvider(success("rapidapi"))

            val result = TranscriptFallbackResolver(
                FakeTranscriptProvider(failure(code)),
                fallback,
            ).fetch("ABCDEFGHIJK", fallbackEnabled = true)

            assertEquals(code, (result as TranscriptFetchResult.Failure).code)
            assertEquals(0, fallback.callCount)
        }
    }

    @Test
    fun enabledFallbackWithoutConfiguredKeyFailsBeforeNetwork() = runBlocking {
        val result = TranscriptFallbackResolver(
            FakeTranscriptProvider(failure("REQUEST_BLOCKED")),
        ).fetch("ABCDEFGHIJK", fallbackEnabled = true)

        assertEquals("RAPIDAPI_KEY_MISSING", (result as TranscriptFetchResult.Failure).code)
    }

    private class FakeTranscriptProvider(
        private val result: TranscriptFetchResult,
    ) : TranscriptProvider {
        var callCount = 0

        override suspend fun fetch(
            videoId: String,
            preferredLanguages: List<String>,
        ): TranscriptFetchResult {
            callCount += 1
            return result
        }
    }

    private fun success(provider: String) = TranscriptFetchResult.Success(
        TranscriptDocument(
            videoId = "ABCDEFGHIJK",
            languageCode = "en",
            isGenerated = false,
            provider = provider,
            segments = listOf(TranscriptSegment("Synthetic", 0.0, 1.0)),
        ),
    )

    private fun failure(code: String) = TranscriptFetchResult.Failure(code)
}
