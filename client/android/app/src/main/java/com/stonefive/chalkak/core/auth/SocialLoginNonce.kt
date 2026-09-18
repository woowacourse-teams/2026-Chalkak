package com.stonefive.chalkak.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object SocialLoginNonce {
    private const val BYTE_COUNT = 32
    private val secureRandom = SecureRandom()
    private val hexDigits = "0123456789abcdef"

    fun generate(): String {
        val bytes = ByteArray(BYTE_COUNT)
        secureRandom.nextBytes(bytes)
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsignedByte = byte.toInt() and 0xff
                append(hexDigits[unsignedByte ushr 4])
                append(hexDigits[unsignedByte and 0x0f])
            }
        }
    }
}
