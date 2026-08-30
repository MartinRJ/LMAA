package de.lmaa.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.lmaa.app.history.LmaaDatabase
import de.lmaa.app.history.ProviderUsageRepository
import de.lmaa.app.secrets.ProviderSecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RapidApiLiveSmokeInstrumentedTest {
    @Test
    fun configuredByok_fetchesTranscriptAndIncrementsLocalUsageExactlyOnce() = runBlocking {
        assumeTrue(
            "Live-RapidAPI-Smoke nur mit explizitem Instrumentation-Argument",
            InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secretStore = ProviderSecretStore.getInstance(context)
        val secretStatus = secretStore.status.first()
        assertTrue("RapidAPI-Key fehlt", secretStatus.hasRapidApiKey)
        val configuration = RapidApiSettingsRepository.getInstance(context).state.first()
        assertTrue(
            "RapidAPI-Betriebsart ist Aus",
            configuration.routingMode != RapidApiRoutingMode.OFF,
        )

        val usageRepository = ProviderUsageRepository(
            LmaaDatabase.getInstance(context).providerUsageDao(),
        )
        val before = usageRepository.rapidApiCurrentMonth.first()
        val result = secretStore.useRapidApiKey { apiKey ->
            RapidApiTranscriptProvider(
                apiKey = apiKey,
                profile = configuration.profile,
                onRequestFinished = usageRepository::recordRapidApiAttempt,
            ).fetch(LIVE_VIDEO_ID, listOf("en", "de"))
        }
        val after = usageRepository.rapidApiCurrentMonth.first()

        assertEquals(before.attempts + 1, after.attempts)
        assertEquals(
            "RapidAPI-Live-Smoke fehlgeschlagen: ${(result as? TranscriptFetchResult.Failure)?.code}",
            true,
            result is TranscriptFetchResult.Success,
        )
        val document = (result as TranscriptFetchResult.Success).document
        assertTrue(document.provider.startsWith("rapidapi:"))
        assertTrue("RapidAPI lieferte keinen Response-Body", !document.rawContent.isNullOrBlank())
        assertEquals(before.successes + 1, after.successes)
        assertEquals("SUCCESS", after.lastStatus)
    }

    private companion object {
        const val LIVE_ARGUMENT = "lmaa.liveRapidApi"
        const val LIVE_VIDEO_ID = "eWRfhZUzrAc"
    }
}
