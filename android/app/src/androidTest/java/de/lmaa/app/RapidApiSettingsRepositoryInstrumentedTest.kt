package de.lmaa.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RapidApiSettingsRepositoryInstrumentedTest {
    @Test
    fun profileAndRoutingPersistAndDefaultsDisableWithoutSecretMaterial() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = RapidApiSettingsRepository.create(
            context,
            "rapidapi-settings-test-${UUID.randomUUID()}.pb",
        )

        assertEquals(RapidApiConfiguration(), repository.state.first())
        val custom = RapidApiProfile.DEFAULT.copy(
            name = "Synthetic",
            successStatusCodes = "200,202",
            maxResponseBytes = 4_096,
        )
        repository.saveProfile(custom)
        repository.setRoutingMode(RapidApiRoutingMode.PREFERRED)
        assertEquals(
            RapidApiConfiguration(custom, RapidApiRoutingMode.PREFERRED),
            repository.state.first(),
        )

        repository.restoreDefaults()
        assertEquals(RapidApiConfiguration(), repository.state.first())
    }
}
