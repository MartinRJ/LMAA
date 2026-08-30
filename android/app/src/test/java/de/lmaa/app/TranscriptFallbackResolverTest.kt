package de.lmaa.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFallbackResolverTest {
    @Test
    fun offUsesOnlyLocalProvider() = runBlocking {
        val local = FakeTranscriptProvider(success("primary"))
        val rapidApi = FakeTranscriptProvider(success("rapidapi"))

        val result = TranscriptFallbackResolver(local, rapidApi).fetch(
            "ABCDEFGHIJK",
            routingMode = RapidApiRoutingMode.OFF,
        ) as TranscriptFetchResult.Success

        assertEquals("primary", result.document.provider)
        assertEquals(1, local.callCount)
        assertEquals(0, rapidApi.callCount)
    }

    @Test
    fun fallbackUsesRapidApiExactlyOnceOnlyForAllowedLocalFailures() = runBlocking {
        listOf("REQUEST_BLOCKED", "REQUEST_FAILED").forEach { code ->
            val local = FakeTranscriptProvider(failure(code))
            val rapidApi = FakeTranscriptProvider(success("rapidapi"))

            val result = TranscriptFallbackResolver(local, rapidApi).fetch(
                "ABCDEFGHIJK",
                routingMode = RapidApiRoutingMode.FALLBACK,
            ) as TranscriptFetchResult.Success

            assertEquals("rapidapi", result.document.provider)
            assertEquals(1, local.callCount)
            assertEquals(1, rapidApi.callCount)
        }
    }

    @Test
    fun fallbackDoesNotSpendQuotaForSemanticLocalFailures() = runBlocking {
        listOf(
            "INVALID_VIDEO_ID",
            "TRANSCRIPTS_DISABLED",
            "NO_TRANSCRIPT",
            "VIDEO_UNAVAILABLE",
            "PYTHON_BRIDGE_ERROR",
            "INTERNAL_ERROR",
        ).forEach { code ->
            val rapidApi = FakeTranscriptProvider(success("rapidapi"))
            val result = TranscriptFallbackResolver(
                FakeTranscriptProvider(failure(code)),
                rapidApi,
            ).fetch("ABCDEFGHIJK", routingMode = RapidApiRoutingMode.FALLBACK)

            assertEquals(code, (result as TranscriptFetchResult.Failure).code)
            assertEquals(0, rapidApi.callCount)
        }
    }

    @Test
    fun preferredUsesRapidApiFirstAndDoesNotCallLocalOnSuccess() = runBlocking {
        val local = FakeTranscriptProvider(success("primary"))
        val rapidApi = FakeTranscriptProvider(success("rapidapi"))

        val result = TranscriptFallbackResolver(local, rapidApi).fetch(
            "ABCDEFGHIJK",
            routingMode = RapidApiRoutingMode.PREFERRED,
        ) as TranscriptFetchResult.Success

        assertEquals("rapidapi", result.document.provider)
        assertEquals(0, local.callCount)
        assertEquals(1, rapidApi.callCount)
    }

    @Test
    fun preferredFallsBackToLocalExactlyOnceForTechnicalRapidApiFailures() = runBlocking {
        listOf(
            "RAPIDAPI_TIMEOUT",
            "RAPIDAPI_NETWORK_ERROR",
            "RAPIDAPI_HTTP_503",
            "RAPIDAPI_QUOTA_EXCEEDED",
            "RAPIDAPI_RESPONSE_TOO_LARGE",
        ).forEach { code ->
            val local = FakeTranscriptProvider(success("primary"))
            val rapidApi = FakeTranscriptProvider(failure(code))

            val result = TranscriptFallbackResolver(local, rapidApi).fetch(
                "ABCDEFGHIJK",
                routingMode = RapidApiRoutingMode.PREFERRED,
            ) as TranscriptFetchResult.Success

            assertEquals("primary", result.document.provider)
            assertEquals(1, rapidApi.callCount)
            assertEquals(1, local.callCount)
        }
    }

    @Test
    fun preferredDoesNotHideInvalidProfileOrMissingKey() = runBlocking {
        listOf("RAPIDAPI_PROFILE_INVALID", "RAPIDAPI_KEY_HEADER_INVALID").forEach { code ->
            val local = FakeTranscriptProvider(success("primary"))
            val result = TranscriptFallbackResolver(
                local,
                FakeTranscriptProvider(failure(code)),
            ).fetch("ABCDEFGHIJK", routingMode = RapidApiRoutingMode.PREFERRED)

            assertEquals(code, (result as TranscriptFetchResult.Failure).code)
            assertEquals(0, local.callCount)
        }
        val missing = TranscriptFallbackResolver(FakeTranscriptProvider(success("primary")))
            .fetch("ABCDEFGHIJK", routingMode = RapidApiRoutingMode.PREFERRED)
        assertEquals("RAPIDAPI_KEY_MISSING", (missing as TranscriptFetchResult.Failure).code)
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
