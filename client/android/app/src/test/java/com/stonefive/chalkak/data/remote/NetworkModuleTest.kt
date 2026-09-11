package com.stonefive.chalkak.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `HTTPS base URL을 허용한다`() {
        val url = "https://example.com/api/v1/".toHttpsBaseUrl()

        assertEquals("https", url.scheme)
    }

    @Test
    fun `HTTP base URL을 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            "http://example.com/api/v1/".toHttpsBaseUrl()
        }
    }
}
