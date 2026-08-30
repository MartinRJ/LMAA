package de.lmaa.app

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidApiTranscriptProviderTest {
    @Test
    fun defaultProfileRendersPlaceholdersAndReturnsBodyUnchanged() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val raw = """{"content":[{"text":"Synthetic\\nraw","offset":1500}]}"""
            server.enqueue(jsonResponse(raw))
            var recordedStatus: Pair<Boolean, String>? = null
            val provider = RapidApiTranscriptProvider(
                apiKey = "rapidapi-synthetic-not-real",
                endpoint = server.url("/youtube/transcript"),
                onRequestFinished = { success, status -> recordedStatus = success to status },
            )

            val result = provider.fetch("ABCDEFGHIJK", listOf("de"))
                as TranscriptFetchResult.Success

            assertEquals(raw, result.document.rawContent)
            assertTrue(result.document.segments.isEmpty())
            assertEquals("rapidapi:youtube-transcripts", result.document.provider)
            val request = server.takeRequest()
            assertEquals("rapidapi-synthetic-not-real", request.headers["X-RapidAPI-Key"])
            assertEquals("youtube-transcripts.p.rapidapi.com", request.headers["X-RapidAPI-Host"])
            assertEquals("ABCDEFGHIJK", request.url.queryParameter("videoId"))
            assertEquals("https://www.youtube.com/watch?v=ABCDEFGHIJK", request.url.queryParameter("url"))
            assertEquals("de", request.url.queryParameter("lang"))
            assertEquals(true to "SUCCESS", recordedStatus)
        } finally {
            server.close()
        }
    }

    @Test
    fun configuredPostBodyAndSuccessStatusAreSupported() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse("{\"ok\":true}", code = 201))
            val profile = RapidApiProfile.DEFAULT.copy(
                method = RapidApiHttpMethod.POST,
                queryParameters = emptyList(),
                headers = RapidApiProfile.DEFAULT.headers +
                    RapidApiTemplateEntry("Content-Type", "application/json; charset=utf-8"),
                bodyTemplate = "{\"video\":\"{{video_id}}\",\"language\":\"{{language}}\"}",
                successStatusCodes = "201",
            )
            val result = RapidApiTranscriptProvider(
                apiKey = "rapidapi-synthetic-not-real",
                profile = profile,
                endpoint = server.url("/transcribe"),
            ).fetch("ABCDEFGHIJK", listOf("en"))

            assertTrue(result is TranscriptFetchResult.Success)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("{\"video\":\"ABCDEFGHIJK\",\"language\":\"en\"}", request.body?.utf8())
        } finally {
            server.close()
        }
    }

    @Test
    fun quotaHttpContentTypeEmptyAndLimitFailuresAreCountedExactlyOnce() = runBlocking {
        val cases = listOf(
            Triple(
                MockResponse.Builder().code(429).build(),
                RapidApiProfile.DEFAULT,
                "RAPIDAPI_QUOTA_EXCEEDED",
            ),
            Triple(
                MockResponse.Builder().code(503).build(),
                RapidApiProfile.DEFAULT,
                "RAPIDAPI_HTTP_503",
            ),
            Triple(
                MockResponse.Builder().code(200).addHeader("Content-Type", "text/html")
                    .body("x").build(),
                RapidApiProfile.DEFAULT,
                "RAPIDAPI_CONTENT_TYPE_NOT_ALLOWED",
            ),
            Triple(jsonResponse(""), RapidApiProfile.DEFAULT, "RAPIDAPI_EMPTY_RESPONSE"),
            Triple(
                jsonResponse("x".repeat(1_025)),
                RapidApiProfile.DEFAULT.copy(maxResponseBytes = 1_024),
                "RAPIDAPI_RESPONSE_TOO_LARGE",
            ),
        )
        cases.forEach { (response, profile, expectedCode) ->
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(response)
                var callbackCount = 0
                var recordedStatus: Pair<Boolean, String>? = null
                val result = RapidApiTranscriptProvider(
                    apiKey = "rapidapi-synthetic-not-real",
                    profile = profile,
                    endpoint = server.url("/youtube/transcript"),
                    onRequestFinished = { success, status ->
                        callbackCount += 1
                        recordedStatus = success to status
                    },
                ).fetch("ABCDEFGHIJK", listOf("en"))

                assertEquals(expectedCode, (result as TranscriptFetchResult.Failure).code)
                assertEquals(1, server.requestCount)
                assertEquals(1, callbackCount)
                assertEquals(false to expectedCode, recordedStatus)
            } finally {
                server.close()
            }
        }
    }

    private fun jsonResponse(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()
}
