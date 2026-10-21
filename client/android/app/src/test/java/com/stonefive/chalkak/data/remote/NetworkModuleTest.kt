package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Authenticator
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `public client never sends credentials or refreshes after unauthorized response`() {
        val store = object : SessionStore {
            override val session = MutableStateFlow<LocalSession>(
                LocalSession.Authenticated(
                    SessionCredentials("user", "token", Long.MAX_VALUE, "refresh", Long.MAX_VALUE),
                ),
            )
            override val sessionState = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
            override suspend fun continueAsGuest(): Unit = error("unused")
            override suspend fun saveSession(credentials: SessionCredentials): Unit = error("unused")
            override suspend fun updateTokens(credentials: SessionCredentials): Boolean = error("must not refresh")
            override suspend fun clear(): Unit = error("must not clear session")
        }
        val network = NetworkModule("https://example.com/api/v1/", store)
        assertTrue(
            network.publicClient.interceptors
                .isEmpty(),
        )
        assertEquals(Authenticator.NONE, network.publicClient.authenticator)
        var calls = 0
        val client = network.publicClient
            .newBuilder()
            .addInterceptor { chain ->
                calls++
                assertNull(chain.request().header("Authorization"))
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{}".toResponseBody())
                    .build()
            }.build()
        client
            .newCall(
                Request
                    .Builder()
                    .url("https://example.com/api/v1/banners?page=1&pageSize=1")
                    .build(),
            ).execute()
            .use { assertEquals(401, it.code) }
        assertEquals(1, calls)
        assertEquals("token", (store.session.value as LocalSession.Authenticated).credentials.accessToken)
    }

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
