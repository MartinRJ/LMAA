package de.lmaa.app

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import de.lmaa.app.rapidapi.proto.RapidApiSettingsData
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal data class RapidApiConfiguration(
    val profile: RapidApiProfile = RapidApiProfile.DEFAULT,
    val routingMode: RapidApiRoutingMode = RapidApiRoutingMode.OFF,
)

internal class RapidApiSettingsRepository private constructor(
    private val dataStore: DataStore<RapidApiSettingsData>,
) {
    val state: Flow<RapidApiConfiguration> = dataStore.data.map(::decode).catch {
        emit(RapidApiConfiguration())
    }

    suspend fun saveProfile(profile: RapidApiProfile) {
        RapidApiProfileValidator.requireValid(profile)
        dataStore.updateData { current ->
            current.toBuilder().setProfileJson(profile.toJson()).build()
        }
    }

    suspend fun setRoutingMode(mode: RapidApiRoutingMode) {
        dataStore.updateData { current ->
            current.toBuilder().setRoutingMode(mode.name).build()
        }
    }

    suspend fun restoreDefaults() {
        dataStore.updateData {
            RapidApiSettingsData.newBuilder()
                .setProfileJson(RapidApiProfile.DEFAULT.toJson())
                .setRoutingMode(RapidApiRoutingMode.OFF.name)
                .build()
        }
    }

    private fun decode(data: RapidApiSettingsData): RapidApiConfiguration {
        val profile = data.profileJson.takeIf(String::isNotBlank)
            ?.let(RapidApiProfile::fromJson)
            ?: RapidApiProfile.DEFAULT
        val mode = runCatching { RapidApiRoutingMode.valueOf(data.routingMode) }
            .getOrDefault(RapidApiRoutingMode.OFF)
        return RapidApiConfiguration(profile, mode)
    }

    companion object {
        private const val DATASTORE_FILE = "lmaa-rapidapi-settings-v1.pb"

        @Volatile
        private var instance: RapidApiSettingsRepository? = null

        fun getInstance(context: Context): RapidApiSettingsRepository = instance
            ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        internal fun create(
            context: Context,
            dataStoreFileName: String = DATASTORE_FILE,
        ): RapidApiSettingsRepository {
            val appContext = context.applicationContext
            return RapidApiSettingsRepository(
                DataStoreFactory.create(
                    serializer = RapidApiSettingsSerializer,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { File(appContext.noBackupFilesDir, dataStoreFileName) },
                ),
            )
        }
    }
}

private object RapidApiSettingsSerializer : Serializer<RapidApiSettingsData> {
    override val defaultValue: RapidApiSettingsData = RapidApiSettingsData.newBuilder()
        .setProfileJson(RapidApiProfile.DEFAULT.toJson())
        .setRoutingMode(RapidApiRoutingMode.OFF.name)
        .build()

    override suspend fun readFrom(input: InputStream): RapidApiSettingsData = try {
        RapidApiSettingsData.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("RapidAPI-Konfiguration ist beschädigt", exception)
    }

    override suspend fun writeTo(t: RapidApiSettingsData, output: OutputStream) {
        t.writeTo(output)
    }
}
