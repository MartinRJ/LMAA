package de.lmaa.app.secrets

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.protobuf.InvalidProtocolBufferException
import de.lmaa.app.secrets.proto.ProviderSecrets
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException

internal class EncryptedProviderSecretsSerializer(
    private val aead: Aead,
) : Serializer<ProviderSecrets> {
    override val defaultValue: ProviderSecrets = ProviderSecrets.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ProviderSecrets {
        return try {
            val encrypted = input.readBytes()
            if (encrypted.isEmpty()) defaultValue else ProviderSecrets.parseFrom(
                aead.decrypt(encrypted, ASSOCIATED_DATA),
            )
        } catch (exception: GeneralSecurityException) {
            throw CorruptionException("Provider-Secrets konnten nicht entschlüsselt werden", exception)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Provider-Secrets sind beschädigt", exception)
        }
    }

    override suspend fun writeTo(t: ProviderSecrets, output: OutputStream) {
        try {
            output.write(aead.encrypt(t.toByteArray(), ASSOCIATED_DATA))
        } catch (exception: GeneralSecurityException) {
            throw SecretStoreUnavailableException(
                "Provider-Secrets konnten nicht verschlüsselt werden",
                exception,
            )
        }
    }

    private companion object {
        val ASSOCIATED_DATA =
            "lmaa/provider-secrets.pb/v1".toByteArray(StandardCharsets.UTF_8)
    }
}
