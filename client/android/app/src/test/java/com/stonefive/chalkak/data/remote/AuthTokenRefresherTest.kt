package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.remote.auth.RefreshApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthTokenRefresherTest {
    private lateinit var server: MockWebServer
    private lateinit var refresher: AuthTokenRefresher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RefreshApi::class.java)
        refresher = AuthTokenRefresher(api, json, currentEpochSeconds = { NOW })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `재발급 성공 시 새 토큰과 만료 시각을 담아 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"accessToken":"new-access","expiresIn":900,"refreshToken":"new-refresh","refreshTokenExpiresIn":2592000}""",
                ),
        )

        val result = refresher.refresh(userId = "user-id", refreshToken = "old-refresh")
        val request = server.takeRequest()

        val success = result as TokenRefreshResult.Success
        assertEquals("user-id", success.credentials.userId)
        assertEquals("new-access", success.credentials.accessToken)
        assertEquals(NOW + 900, success.credentials.expiresAtEpochSeconds)
        assertEquals("new-refresh", success.credentials.refreshToken)
        assertEquals(NOW + 2_592_000, success.credentials.refreshTokenExpiresAtEpochSeconds)
        assertEquals("/api/v1/auth/refresh", request.path)
        assertEquals("""{"refreshToken":"old-refresh"}""", request.body.readUtf8())
    }

    @Test
    fun `errorCode가 REAUTHENTICATION_REQUIRED면 재로그인 필요를 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"REAUTHENTICATION_REQUIRED"}"""),
        )

        val result = refresher.refresh(userId = "user-id", refreshToken = "old-refresh")

        assertEquals(TokenRefreshResult.ReauthenticationRequired, result)
    }

    @Test
    fun `서버 오류는 일시적 실패로 반환한다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = refresher.refresh(userId = "user-id", refreshToken = "old-refresh")

        assertEquals(TokenRefreshResult.TransientFailure, result)
    }

    @Test
    fun `필수 토큰이 빠진 응답은 일시적 실패로 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accessToken":"new-access","expiresIn":900}"""),
        )

        val result = refresher.refresh(userId = "user-id", refreshToken = "old-refresh")

        assertTrue(result is TokenRefreshResult.TransientFailure)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
