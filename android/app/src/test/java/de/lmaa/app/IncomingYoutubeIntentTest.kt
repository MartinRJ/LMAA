package de.lmaa.app

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingYoutubeIntentTest {
    private val url = "https://youtu.be/dQw4w9WgXcQ"

    @Test
    fun `text shares provide their trimmed extra text`() {
        assertEquals(
            url,
            extractIncomingYoutubeText(
                action = Intent.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = "  $url  ",
            ),
        )
    }

    @Test
    fun `unrelated or empty intents are ignored`() {
        assertNull(
            extractIncomingYoutubeText(
                action = Intent.ACTION_SEND,
                mimeType = "image/png",
                sharedText = url,
            ),
        )
        assertNull(
            extractIncomingYoutubeText(
                action = Intent.ACTION_SEND,
                mimeType = "text/html",
                sharedText = url,
            ),
        )
        assertNull(
            extractIncomingYoutubeText(
                action = Intent.ACTION_VIEW,
                mimeType = null,
                sharedText = url,
            ),
        )
    }
}
