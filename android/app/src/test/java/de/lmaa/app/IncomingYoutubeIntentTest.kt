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
    fun `history relaunch does not redeliver the old share`() {
        assertNull(extractIncomingYoutubeText(Intent.ACTION_SEND, "text/plain", url,
            Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertEquals(url, extractIncomingYoutubeText(Intent.ACTION_SEND, "text/plain", url, 0))
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
