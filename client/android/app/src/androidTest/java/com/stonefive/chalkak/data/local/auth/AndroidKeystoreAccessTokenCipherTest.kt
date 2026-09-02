package com.stonefive.chalkak.data.local.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidKeystoreAccessTokenCipherTest {
    private val cipher = AndroidKeystoreAccessTokenCipher()

    @Test
    fun encryptAndDecryptAccessTokenWithAndroidKeystoreKey() {
        val accessToken = "access-token"

        val encryptedAccessToken = cipher.encrypt(accessToken)

        assertFalse(encryptedAccessToken.contains(accessToken))
        assertEquals(accessToken, cipher.decrypt(encryptedAccessToken))
    }

    @Test
    fun returnNullForCorruptedCiphertext() {
        assertEquals(null, cipher.decrypt("invalid-ciphertext"))
    }
}
