package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserIdHeaderInterceptorTest {
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
    fun `저장된 userId를 백엔드 요청 헤더에 추가한다`() {
        val store = HeaderTestSessionStore(userId = "user-id")
        val client = OkHttpClient
            .Builder()
            .addInterceptor(UserIdHeaderInterceptor(store))
            .build()

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .build(),
            ).execute()
            .close()

        assertEquals("user-id", server.takeRequest().headers["X-User-Id"])
    }

    @Test
    fun `userId가 없으면 인증 헤더를 추가하지 않는다`() {
        val client = OkHttpClient
            .Builder()
            .addInterceptor(UserIdHeaderInterceptor(HeaderTestSessionStore()))
            .build()

        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .build(),
            ).execute()
            .close()

        assertNull(server.takeRequest().headers["X-User-Id"])
    }
}

private class HeaderTestSessionStore(userId: String? = null) : SessionStore {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(
        userId?.let(UserSessionState::Authenticated) ?: UserSessionState.SignedOut,
    )

    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun saveUserId(userId: String) {
        mutableSessionState.value = UserSessionState.Authenticated(userId)
    }

    override suspend fun clear() {
        mutableSessionState.value = UserSessionState.SignedOut
    }
}
