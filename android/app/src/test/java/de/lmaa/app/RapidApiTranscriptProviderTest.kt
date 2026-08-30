package de.lmaa.app

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidApiTranscriptProviderTest {
    @Test
    fun requestUsesSensitiveHeadersAndNormalizesMilliseconds() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{"lang":"en","isGenerated":true,"content":[{"text":"Synthetic","offset":1500,"duration":2000}]}""",
                    )
                    .build(),
            )
            var recordedStatus: Pair<Boolean, String>? = null
            val provider = RapidApiTranscriptProvider(
                apiKey = "rapidapi-synthetic-not-real",
                endpoint = server.url("/youtube/transcript"),
                onRequestFinished = { success, status -> recordedStatus = success to status },
            )

            val result = provider.fetch("ABCDEFGHIJK", listOf("en"))
                as TranscriptFetchResult.Success

            assertEquals("rapidapi", result.document.provider)
            assertTrue(result.document.isGenerated)
            assertEquals(TranscriptSegment("Synthetic", 1.5, 2.0), result.document.segments.single())
            val request = server.takeRequest()
            assertEquals("rapidapi-synthetic-not-real", request.headers["X-RapidAPI-Key"])
            assertEquals("youtube-transcripts.p.rapidapi.com", request.headers["X-RapidAPI-Host"])
            assertEquals("ABCDEFGHIJK", request.url.queryParameter("videoId"))
            assertEquals("false", request.url.queryParameter("text"))
            assertEquals(true to "SUCCESS", recordedStatus)
        } finally {
            server.close()
        }
    }

    @Test
    fun quotaResponseIsNotRetried() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(429).build())
            var recordedStatus: Pair<Boolean, String>? = null
            val provider = RapidApiTranscriptProvider(
                apiKey = "rapidapi-synthetic-not-real",
                endpoint = server.url("/youtube/transcript"),
                onRequestFinished = { success, status -> recordedStatus = success to status },
            )

            val result = provider.fetch("ABCDEFGHIJK", listOf("en"))

            assertEquals(
                "RAPIDAPI_QUOTA_EXCEEDED",
                (result as TranscriptFetchResult.Failure).code,
            )
            assertEquals(1, server.requestCount)
            assertEquals(false to "RAPIDAPI_QUOTA_EXCEEDED", recordedStatus)
        } finally {
            server.close()
        }
    }
}
