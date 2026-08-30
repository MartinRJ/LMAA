package de.lmaa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUrlParserTest {
    private val videoId = "dQw4w9WgXcQ"
    private val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"

    @Test
    fun `supported URLs are canonicalized`() {
        val inputs = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId&t=12",
            "http://m.youtube.com/watch?feature=share&v=$videoId",
            "https://youtu.be/$videoId?si=synthetic",
            "https://www.youtube.com/shorts/$videoId",
            "https://www.youtube.com/live/$videoId?feature=share",
            "Schau dir das an: https://youtu.be/$videoId?si=synthetic Danke!",
        )

        inputs.forEach { input ->
            assertEquals(
                YoutubeUrlParseResult.Success(videoId, canonicalUrl),
                YoutubeUrlParser.parse(input),
            )
        }
    }

    @Test
    fun `unsafe and unsupported URLs are rejected`() {
        val inputs = listOf(
            "kein Link",
            "https://youtube.com.evil.example/watch?v=$videoId",
            "https://youtube.com@evil.example/watch?v=$videoId",
            "https://www.youtube.com:8443/watch?v=$videoId",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/embed/$videoId",
            "https://youtu.be/$videoId/extra",
            "https://www.youtube.com/watch?v=$videoId&v=AAAAAAAAAAA",
        )

        inputs.forEach { input ->
            assertEquals(YoutubeUrlParseResult.Error.INVALID, YoutubeUrlParser.parse(input))
        }
    }

    @Test
    fun `blank input is distinguished from invalid input`() {
        assertEquals(YoutubeUrlParseResult.Error.EMPTY, YoutubeUrlParser.parse("  "))
    }

    @Test
    fun `two different valid URLs are ambiguous`() {
        val result = YoutubeUrlParser.parse(
            "https://youtu.be/$videoId https://youtu.be/AAAAAAAAAAA",
        )

        assertEquals(YoutubeUrlParseResult.Error.AMBIGUOUS, result)
    }
}
