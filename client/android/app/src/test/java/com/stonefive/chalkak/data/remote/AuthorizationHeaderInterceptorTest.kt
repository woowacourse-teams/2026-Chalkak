package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthorizationHeaderInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `저장된 access token을 백엔드 요청 Authorization 헤더에 추가한다`() {
        val store = HeaderTestSessionStore(token = "access-token")
        lateinit var interceptedRequest: Request
        val client = OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationHeaderInterceptor(store))
            .addInterceptor { chain ->
                interceptedRequest = chain.request()
                Response
                    .Builder()
                    .request(interceptedRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }.build()

        client
            .newCall(
                Request
                    .Builder()
                    .url("https://example.com/")
                    .build(),
            ).execute()
            .close()

        assertEquals("Bearer access-token", interceptedRequest.header("Authorization"))
    }

    @Test
    fun `HTTP 요청에는 기존 Authorization 헤더도 전송하지 않는다`() {
        val client = OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationHeaderInterceptor(HeaderTestSessionStore(token = "access-token")))
            .build()

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .header("Authorization", "Bearer existing-token")
                    .build(),
            ).execute()
            .close()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `access token이 없으면 인증 헤더를 추가하지 않는다`() {
        val client = OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationHeaderInterceptor(HeaderTestSessionStore()))
            .build()

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .build(),
            ).execute()
            .close()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `만료된 access token은 인증 헤더에 추가하지 않는다`() {
        val client = OkHttpClient
            .Builder()
            .addInterceptor(
                AuthorizationHeaderInterceptor(
                    sessionStore = HeaderTestSessionStore(
                        token = "expired-token",
                        expiresAtEpochSeconds = 99,
                    ),
                    currentEpochSeconds = { 100 },
                ),
            ).build()

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .build(),
            ).execute()
            .close()

        assertNull(server.takeRequest().headers["Authorization"])
    }
}

private class HeaderTestSessionStore(
    token: String? = null,
    expiresAtEpochSeconds: Long = Long.MAX_VALUE,
) : SessionStore {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(
        token?.let { UserSessionState.Authenticated("user-id") } ?: UserSessionState.SignedOut,
    )
    private val mutableSession = MutableStateFlow<LocalSession>(
        token?.let {
            LocalSession.Authenticated(
                SessionCredentials(
                    userId = "user-id",
                    accessToken = it,
                    expiresAtEpochSeconds = expiresAtEpochSeconds,
                    refreshToken = "refresh-token",
                    refreshTokenExpiresAtEpochSeconds = Long.MAX_VALUE,
                ),
            )
        } ?: LocalSession.SignedOut,
    )

    override val session: StateFlow<LocalSession> = mutableSession
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun continueAsGuest() {
        mutableSession.value = LocalSession.Guest
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun saveSession(credentials: SessionCredentials) {
        mutableSession.value = LocalSession.Authenticated(credentials)
        mutableSessionState.value = UserSessionState.Authenticated(credentials.userId)
    }

    override suspend fun updateTokens(credentials: SessionCredentials): Boolean {
        val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials
        if (current == null || current.userId != credentials.userId) return false
        saveSession(credentials)
        return true
    }

    override suspend fun clear() {
        mutableSession.value = LocalSession.SignedOut
        mutableSessionState.value = UserSessionState.SignedOut
    }
}
