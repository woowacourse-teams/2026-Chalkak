package com.stonefive.chalkak.data.local.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidKeystoreTokenCipherTest {
    private val cipher = AndroidKeystoreTokenCipher()

    @Test
    fun encryptAndDecryptTokenWithAndroidKeystoreKey() {
        val token = "access-token"

        val encryptedToken = cipher.encrypt(token)

        assertFalse(encryptedToken.contains(token))
        assertEquals(token, cipher.decrypt(encryptedToken))
    }

    @Test
    fun returnNullForCorruptedCiphertext() {
        assertEquals(null, cipher.decrypt("invalid-ciphertext"))
    }
}
