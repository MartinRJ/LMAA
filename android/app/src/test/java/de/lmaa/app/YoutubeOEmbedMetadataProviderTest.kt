package de.lmaa.app

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeOEmbedMetadataProviderTest {
    @Test
    fun fetchUsesCanonicalUrlAndKeepsUnavailableFieldsNull() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{"title":"Testvideo","author_name":"Testkanal","thumbnail_url":"https://i.ytimg.com/vi/ABCDEFGHIJK/hqdefault.jpg","html":"<iframe>ignored</iframe>"}""",
                    )
                    .build(),
            )
            val provider = YoutubeOEmbedMetadataProvider(endpoint = server.url("/oembed"))

            val result = provider.fetch("ABCDEFGHIJK") as MetadataFetchResult.Success

            assertEquals("Testvideo", result.metadata.title)
            assertEquals("Testkanal", result.metadata.channelTitle)
            assertNull(result.metadata.channelId)
            assertNull(result.metadata.publishedAt)
            assertNull(result.metadata.durationSeconds)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("json", request.url.queryParameter("format"))
            assertEquals(
                "https://www.youtube.com/watch?v=ABCDEFGHIJK",
                request.url.queryParameter("url"),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun rejectsNonHttpsThumbnail() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{"title":"Testvideo","author_name":"Testkanal","thumbnail_url":"http://example.test/thumb.jpg"}""",
                    )
                    .build(),
            )

            val result = YoutubeOEmbedMetadataProvider(endpoint = server.url("/oembed"))
                .fetch("ABCDEFGHIJK")

            assertTrue(result is MetadataFetchResult.Failure)
            assertEquals("OEMBED_MALFORMED_RESPONSE", (result as MetadataFetchResult.Failure).code)
        } finally {
            server.close()
        }
    }
}
