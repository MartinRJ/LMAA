package de.lmaa.app.secrets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import de.lmaa.app.secrets.proto.ProviderSecrets
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ProviderSecretStatus(
    val isAvailable: Boolean,
    val hasOpenAiKey: Boolean,
    val hasRapidApiKey: Boolean,
    val rapidApiEnabled: Boolean,
)

class ProviderSecretStore private constructor(
    private val dataStore: DataStore<ProviderSecrets>,
    private val keysetRepository: TinkKeysetRepository,
) {
    val status: Flow<ProviderSecretStatus> = dataStore.data.map { secrets ->
        ProviderSecretStatus(
            isAvailable = true,
            hasOpenAiKey = secrets.openaiApiKey.isNotEmpty(),
            hasRapidApiKey = secrets.rapidapiKey.isNotEmpty(),
            rapidApiEnabled = secrets.rapidapiEnabled,
        )
    }.catch {
        emit(
            ProviderSecretStatus(
                isAvailable = false,
                hasOpenAiKey = false,
                hasRapidApiKey = false,
                rapidApiEnabled = false,
            ),
        )
    }

    suspend fun saveOpenAiKey(apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "OpenAI-Key darf nicht leer sein" }
        dataStore.updateData { current ->
            current.toBuilder().setOpenaiApiKey(normalized).build()
        }
    }

    suspend fun clearOpenAiKey() {
        dataStore.updateData { current -> current.toBuilder().clearOpenaiApiKey().build() }
    }

    suspend fun <T> useOpenAiKey(block: suspend (String) -> T): T {
        val apiKey = dataStore.data.first().openaiApiKey
        check(apiKey.isNotEmpty()) { "Kein OpenAI-Key gespeichert" }
        return block(apiKey)
    }

    internal fun deleteTestMaterial() {
        keysetRepository.deleteForTest()
    }

    companion object {
        const val MASK = "****"
        private const val DEFAULT_KEYSTORE_ALIAS = "lmaa.provider-secrets.master.v1"
        private const val DEFAULT_KEYSET_FILE = "lmaa-provider-secrets-keyset-v1.bin"
        private const val DEFAULT_DATASTORE_FILE = "lmaa-provider-secrets-v1.pb"

        @Volatile
        private var instance: ProviderSecretStore? = null

        fun getInstance(context: Context): ProviderSecretStore = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        internal fun create(
            context: Context,
            keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
            keysetFileName: String = DEFAULT_KEYSET_FILE,
            dataStoreFileName: String = DEFAULT_DATASTORE_FILE,
        ): ProviderSecretStore {
            val appContext = context.applicationContext
            val keysetRepository = TinkKeysetRepository(
                context = appContext,
                keystoreAlias = keystoreAlias,
                keysetFileName = keysetFileName,
            )
            val serializer = EncryptedProviderSecretsSerializer(
                keysetRepository.loadOrCreateDataAead(),
            )
            val dataStore = DataStoreFactory.create(
                serializer = serializer,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { File(appContext.noBackupFilesDir, dataStoreFileName) },
            )
            return ProviderSecretStore(dataStore, keysetRepository)
        }
    }
}
