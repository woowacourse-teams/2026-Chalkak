package com.stonefive.chalkak.data.local.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipher {
    fun encrypt(token: String): String

    fun decrypt(encryptedToken: String): String?
}

class AndroidKeystoreTokenCipher : TokenCipher {
    override fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedToken = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64
            .getEncoder()
            .encodeToString(cipher.iv + encryptedToken)
    }

    override fun decrypt(encryptedToken: String): String? = runCatching {
        val encryptedBytes = Base64
            .getDecoder()
            .decode(encryptedToken)
        require(encryptedBytes.size > IV_SIZE_BYTES)
        val initializationVector = encryptedBytes.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = encryptedBytes.copyOfRange(IV_SIZE_BYTES, encryptedBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector),
        )
        cipher
            .doFinal(ciphertext)
            .toString(Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore
            .getInstance(ANDROID_KEYSTORE)
            .apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "chalkak_access_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
