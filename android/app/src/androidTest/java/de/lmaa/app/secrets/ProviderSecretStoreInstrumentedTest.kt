package de.lmaa.app.secrets

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderSecretStoreInstrumentedTest {
    @Test
    fun roundTripPersistsOnlyCiphertextAndClearsAtomically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = UUID.randomUUID().toString()
        val dataStoreFileName = "provider-secrets-$suffix.pb"
        val store = ProviderSecretStore.create(
            context = context,
            keystoreAlias = "lmaa.test.$suffix",
            keysetFileName = "provider-keyset-$suffix.bin",
            dataStoreFileName = dataStoreFileName,
        )
        val syntheticKey = "sk-test-only-not-a-real-key"
        val syntheticRapidApiKey = "rapidapi-test-only-not-a-real-key"

        try {
            assertFalse(store.status.first().hasOpenAiKey)
            store.saveOpenAiKey(syntheticKey)
            assertTrue(store.status.first().hasOpenAiKey)
            assertEquals(syntheticKey, store.useOpenAiKey { it })
            store.saveRapidApiKey(syntheticRapidApiKey)
            store.setRapidApiEnabled(true)
            assertTrue(store.status.first().hasRapidApiKey)
            assertTrue(store.status.first().rapidApiEnabled)
            assertEquals(syntheticRapidApiKey, store.useRapidApiKey { it })

            val persisted = File(context.noBackupFilesDir, dataStoreFileName).readBytes()
            assertFalse(persisted.toString(Charsets.UTF_8).contains(syntheticKey))
            assertFalse(persisted.toString(Charsets.UTF_8).contains(syntheticRapidApiKey))

            store.clearRapidApiKey()
            assertFalse(store.status.first().hasRapidApiKey)
            assertFalse(store.status.first().rapidApiEnabled)

            store.clearOpenAiKey()
            assertFalse(store.status.first().hasOpenAiKey)
        } finally {
            File(context.noBackupFilesDir, dataStoreFileName).delete()
            store.deleteTestMaterial()
        }
    }
}
