package de.lmaa.app.secrets

import android.content.Context
import android.util.AtomicFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException

internal class TinkKeysetRepository(
    context: Context,
    private val keystoreAlias: String,
    keysetFileName: String,
) {
    private val keysetFile = AtomicFile(File(context.noBackupFilesDir, keysetFileName))

    @Synchronized
    fun loadOrCreateDataAead(): Aead {
        try {
            AeadConfig.register()
            val exists = keysetFile.baseFile.exists()
            val wrappingAead = AndroidKeystoreAead.loadOrCreate(
                alias = keystoreAlias,
                allowCreate = !exists,
            )
            val handle = if (exists) {
                val serialized = keysetFile.openRead().use { input -> input.readBytes() }
                TinkProtoKeysetFormat.parseEncryptedKeyset(
                    serialized,
                    wrappingAead,
                    KEYSET_ASSOCIATED_DATA,
                    RegistryConfiguration.get(),
                )
            } else {
                KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM).also { handle ->
                    val serialized = TinkProtoKeysetFormat.serializeEncryptedKeyset(
                        handle,
                        wrappingAead,
                        KEYSET_ASSOCIATED_DATA,
                        RegistryConfiguration.get(),
                    )
                    writeAtomically(serialized)
                }
            }
            return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        } catch (exception: GeneralSecurityException) {
            throw SecretStoreUnavailableException("Secret-Store konnte nicht entsperrt werden", exception)
        } catch (exception: IOException) {
            throw SecretStoreUnavailableException("Secret-Store konnte nicht gelesen werden", exception)
        }
    }

    private fun writeAtomically(bytes: ByteArray) {
        val output = keysetFile.startWrite()
        try {
            output.write(bytes)
            keysetFile.finishWrite(output)
        } catch (exception: IOException) {
            keysetFile.failWrite(output)
            throw exception
        }
    }

    internal fun deleteForTest() {
        keysetFile.delete()
        AndroidKeystoreAead.delete(keystoreAlias)
    }

    private companion object {
        val KEYSET_ASSOCIATED_DATA =
            "lmaa/provider-secret-keyset/v1".toByteArray(StandardCharsets.UTF_8)
    }
}

class SecretStoreUnavailableException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)
