package de.lmaa.app.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.Aead
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidKeystoreAead private constructor(
    private val secretKey: SecretKey,
) : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size <= UByte.MAX_VALUE.toInt()) { "Keystore-IV ist zu lang" }
        return ByteBuffer.allocate(HEADER_SIZE + iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < HEADER_SIZE + MIN_GCM_IV_SIZE + GCM_TAG_SIZE_BYTES) {
            throw GeneralSecurityException("Ungültiger Keystore-Ciphertext")
        }
        val buffer = ByteBuffer.wrap(ciphertext)
        if (buffer.get() != FORMAT_VERSION) {
            throw GeneralSecurityException("Nicht unterstütztes Keystore-Ciphertextformat")
        }
        val ivSize = buffer.get().toUByte().toInt()
        if (ivSize < MIN_GCM_IV_SIZE || buffer.remaining() < ivSize + GCM_TAG_SIZE_BYTES) {
            throw GeneralSecurityException("Ungültiger Keystore-IV")
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val encryptedPayload = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return cipher.doFinal(encryptedPayload)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_SIZE_BITS = 128
        private const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / 8
        private const val MIN_GCM_IV_SIZE = 12
        private const val HEADER_SIZE = 2
        private const val FORMAT_VERSION: Byte = 1

        fun loadOrCreate(alias: String, allowCreate: Boolean): AndroidKeystoreAead {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(alias, null) as? SecretKey
            if (existingKey != null) return AndroidKeystoreAead(existingKey)
            if (!allowCreate) {
                throw GeneralSecurityException("Keystore-Schlüssel fehlt")
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return AndroidKeystoreAead(generator.generateKey())
        }

        fun delete(alias: String) {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }
}
