package de.lmaa.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingYoutubeIntentRegistrationInstrumentedTest {
    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun onlyPlainTextSharesResolveToLmaa() {
        val sharedUrl = "https://youtu.be/dQw4w9WgXcQ"
        assertTrue(
            resolvesToLmaa(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, sharedUrl)
                },
            ),
        )
        assertFalse(
            resolvesToLmaa(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_TEXT, sharedUrl)
                },
            ),
        )

        assertFalse(
            resolvesToLmaa(
                Intent(Intent.ACTION_VIEW, Uri.parse(sharedUrl)),
            ),
        )
        assertFalse(
            resolvesToLmaa(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_TEXT, sharedUrl)
                },
            ),
        )
    }

    private fun resolvesToLmaa(intent: Intent): Boolean =
        targetContext.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .any { resolved ->
                resolved.activityInfo.packageName == targetContext.packageName &&
                    resolved.activityInfo.name == MainActivity::class.java.name
            }
}
