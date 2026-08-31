package com.rectime.mobile.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeystoreCipher(private val alias: String) : ValueCipher {

    override fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        packSecureEnvelope(cipher.iv, ciphertext)
    }.getOrNull()

    override fun decrypt(value: String): String? {
        val envelope = unpackSecureEnvelope(value) ?: return null
        val key = existingSecretKey() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.nonce))
            cipher.doFinal(envelope.ciphertext).decodeToString()
        }.getOrNull()
    }

    override fun discardKey() {
        synchronized(KEY_LOCK) {
            runCatching { keyStore()?.deleteEntry(alias) }
        }
    }

    private fun secretKey(): SecretKey? = synchronized(KEY_LOCK) {
        existingSecretKey() ?: generateSecretKey()
    }

    private fun existingSecretKey(): SecretKey? = runCatching {
        (keyStore()?.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }.getOrNull()

    private fun generateSecretKey(): SecretKey? = runCatching {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_SIZE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
    }.getOrNull()

    private fun keyStore(): KeyStore? = runCatching {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }.getOrNull()

    private companion object {
        val KEY_LOCK = Any()

        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE = 256
    }
}
