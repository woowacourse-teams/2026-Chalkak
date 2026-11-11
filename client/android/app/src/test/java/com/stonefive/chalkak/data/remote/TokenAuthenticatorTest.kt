package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401이면 토큰을 재발급하고 새 access token으로 원요청을 재시도한다`() {
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token"))
        val refresher = FakeTokenRefresher(
            TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token")),
        )
        val client = clientWith(store, refresher)
        server.enqueue(unauthorizedResponse())
        server.enqueue(MockResponse().setResponseCode(200))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals(1, refresher.callCount.get())
        server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("Bearer new-access-token", retried.headers["Authorization"])
        assertEquals("new-access-token", currentAccessToken(store))
        response.close()
    }

    // 기존 `원요청 401이 REAUTHENTICATION_REQUIRED면 재발급하지 않고 세션을 정리한다`(SignedOut 기대)를 #1016 TC 2로 바꿈
    @Test
    fun `원요청이 REAUTHENTICATION_REQUIRED면 갱신 없이 재인증 필요 상태가 된다`() {
        // Given "u1" 로그인
        // When  일반 API가 401 REAUTHENTICATION_REQUIRED
        // Then  갱신 요청 0회, 세션 상태 ReauthenticationRequired("u1")
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token", userId = "u1"))
        val refresher = FakeTokenRefresher(
            TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token", userId = "u1")),
        )
        val client = clientWith(store, refresher)
        server.enqueue(reauthenticationRequiredResponse())

        val response = client.newCall(request()).execute()

        assertEquals(401, response.code)
        assertEquals(0, refresher.callCount.get())
        assertEquals(UserSessionState.ReauthenticationRequired("u1"), store.sessionState.value)
        response.close()
    }

    // 기존 `재발급이 재로그인 필요를 반환하면 세션을 정리하고 재시도하지 않는다`(SignedOut 기대)를 #1016 TC 1로 바꿈
    @Test
    fun `Refresh 토큰이 거절되면 토큰을 지우고 같은 계정의 재인증 필요 상태가 된다`() {
        // Given "u1" 로그인, 갱신 요청이 401 REAUTHENTICATION_REQUIRED (갱신기가 ReauthenticationRequired를 돌려줌)
        // When  일반 API가 401 UNAUTHORIZED
        // Then  세션 상태 ReauthenticationRequired("u1"), 저장된 Access·Refresh 토큰 없음, 원요청 재시도 0회
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token", userId = "u1"))
        val refresher = FakeTokenRefresher(TokenRefreshResult.ReauthenticationRequired)
        val client = clientWith(store, refresher)
        server.enqueue(unauthorizedResponse())

        val response = client.newCall(request()).execute()

        assertEquals(401, response.code)
        assertEquals(1, refresher.callCount.get())
        assertEquals(UserSessionState.ReauthenticationRequired("u1"), store.sessionState.value)
        assertNull(currentAccessToken(store))
        assertEquals(1, server.requestCount)
        response.close()
    }

    // 기존 `재발급이 일시적으로 실패하면 세션을 유지하고 재시도를 포기한다`(401 기대)를 #1016 TC 3으로 바꿈
    @Test
    fun `갱신이 네트워크 오류로 실패하면 원요청은 네트워크 오류로 끝나고 로그인은 유지된다`() {
        // Given "u1" 로그인(AT "at-1"), 갱신 요청이 IOException (갱신기가 TransientFailure를 돌려줌)
        // When  일반 API가 401 UNAUTHORIZED
        // Then  호출자는 401 응답이 아니라 IOException을 받음, 세션 상태 Authenticated("u1"), AT "at-1" 그대로
        val store = FakeSessionStore(authenticated("at-1", "rt-1", userId = "u1"))
        val refresher = FakeTokenRefresher(TokenRefreshResult.TransientFailure)
        val client = clientWith(store, refresher)
        server.enqueue(unauthorizedResponse())

        val outcome = runCatching {
            client
                .newCall(request())
                .execute()
                .use { it.code }
        }

        assertTrue(
            "401 응답이 아니라 IOException이어야 함: $outcome",
            outcome.exceptionOrNull() is IOException,
        )
        assertEquals(1, refresher.callCount.get())
        assertEquals(UserSessionState.Authenticated("u1"), store.sessionState.value)
        assertEquals("at-1", currentAccessToken(store))
    }

    @Test
    fun `이미 다른 요청이 재발급했다면 재발급 없이 새 토큰으로 재시도한다`() {
        val store = FakeSessionStore(authenticated("new-access-token", "new-refresh-token"))
        val refresher = FakeTokenRefresher(TokenRefreshResult.TransientFailure)
        val authenticator = TokenAuthenticator(store, refresher, json)
        val staleResponse = response401TaggedWith("old-access-token")

        val retry = authenticator.authenticate(null, staleResponse)

        assertEquals("Bearer new-access-token", retry?.header("Authorization"))
        assertEquals(0, refresher.callCount.get())
    }

    @Test
    fun `재발급이 완료되기 전에 로그아웃되면 세션을 되살리지 않고 재시도하지 않는다`() {
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token"))
        val refresher = MutatingTokenRefresher(
            result = TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token")),
            beforeReturn = { store.clear() },
        )
        val authenticator = TokenAuthenticator(store, refresher, json)

        val retry = authenticator.authenticate(null, response401TaggedWith("old-access-token"))

        assertNull(retry)
        assertEquals(UserSessionState.SignedOut, store.sessionState.value)
    }

    @Test
    fun `재발급이 완료되기 전에 다른 계정으로 로그인되면 기존 사용자를 덮지 않는다`() {
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token"))
        val refresher = MutatingTokenRefresher(
            result = TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token")),
            beforeReturn = {
                store.saveSession(
                    authenticated("user2-access-token", "user2-refresh-token", userId = "user-2"),
                )
            },
        )
        val authenticator = TokenAuthenticator(store, refresher, json)

        val retry = authenticator.authenticate(null, response401TaggedWith("old-access-token"))

        assertNull(retry)
        assertEquals(UserSessionState.Authenticated("user-2"), store.sessionState.value)
        assertEquals("user2-access-token", currentAccessToken(store))
    }

    @Test
    fun `재발급 후 재시도까지 실패하면 더 이상 재발급하지 않는다`() {
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token"))
        val refresher = FakeTokenRefresher(
            TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token")),
        )
        val authenticator = TokenAuthenticator(store, refresher, json)
        val retriedResponse = response401TaggedWith("old-access-token", hasPriorResponse = true)

        val retry = authenticator.authenticate(null, retriedResponse)

        assertNull(retry)
        assertEquals(0, refresher.callCount.get())
    }

    @Test
    fun `여러 요청이 동시에 401을 받아도 재발급은 한 번만 진행된다`() {
        val store = FakeSessionStore(authenticated("old-access-token", "old-refresh-token"))
        val refresher = FakeTokenRefresher(
            TokenRefreshResult.Success(authenticated("new-access-token", "new-refresh-token")),
        )
        val client = clientWith(store, refresher)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.headers["Authorization"] == "Bearer new-access-token") {
                    MockResponse().setResponseCode(200)
                } else {
                    unauthorizedResponse()
                }
        }

        val concurrency = 8
        val executor = Executors.newFixedThreadPool(concurrency)
        val startLatch = CountDownLatch(1)
        val codes = java.util.concurrent
            .ConcurrentHashMap<Int, Int>()
        repeat(concurrency) { index ->
            executor.submit {
                startLatch.await()
                client
                    .newCall(request())
                    .execute()
                    .use { codes[index] = it.code }
            }
        }
        startLatch.countDown()
        executor.shutdown()
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(List(concurrency) { 200 }, codes.values.sorted())
        assertEquals(1, refresher.callCount.get())
    }

    private fun clientWith(
        store: SessionStore,
        refresher: TokenRefresher,
    ): OkHttpClient = OkHttpClient
        .Builder()
        .addInterceptor { chain ->
            val accessToken = (store.session.value as? LocalSession.Authenticated)
                ?.credentials
                ?.accessToken
            val request = if (accessToken != null) {
                chain
                    .request()
                    .newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .tag(
                        AuthorizationRequestContext::class.java,
                        AuthorizationRequestContext(accessToken),
                    ).build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }.authenticator(TokenAuthenticator(store, refresher, json))
        .build()

    private fun request(): Request = Request
        .Builder()
        .url(server.url("/api/v1/posts"))
        .build()

    private fun unauthorizedResponse(): MockResponse = MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"errorCode":"UNAUTHORIZED"}""")

    private fun reauthenticationRequiredResponse(): MockResponse = MockResponse()
        .setResponseCode(401)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"errorCode":"REAUTHENTICATION_REQUIRED"}""")

    private fun response401TaggedWith(
        accessToken: String,
        hasPriorResponse: Boolean = false,
    ): Response {
        val request = Request
            .Builder()
            .url(server.url("/api/v1/posts"))
            .tag(
                AuthorizationRequestContext::class.java,
                AuthorizationRequestContext(accessToken),
            ).build()
        val builder = Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("""{"errorCode":"UNAUTHORIZED"}""".toResponseBody())
        if (hasPriorResponse) {
            builder.priorResponse(
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .build(),
            )
        }
        return builder.build()
    }

    private fun currentAccessToken(store: FakeSessionStore): String? =
        (store.session.value as? LocalSession.Authenticated)?.credentials?.accessToken

    private fun authenticated(
        accessToken: String,
        refreshToken: String,
        userId: String = "user-id",
    ): SessionCredentials = SessionCredentials(
        userId = userId,
        accessToken = accessToken,
        expiresAtEpochSeconds = Long.MAX_VALUE,
        refreshToken = refreshToken,
        refreshTokenExpiresAtEpochSeconds = Long.MAX_VALUE,
    )
}

private class FakeTokenRefresher(private val result: TokenRefreshResult) : TokenRefresher {
    val callCount = AtomicInteger(0)

    override suspend fun refresh(
        userId: String,
        refreshToken: String,
    ): TokenRefreshResult {
        callCount.incrementAndGet()
        return result
    }
}

private class MutatingTokenRefresher(
    private val result: TokenRefreshResult,
    private val beforeReturn: suspend () -> Unit,
) : TokenRefresher {
    override suspend fun refresh(
        userId: String,
        refreshToken: String,
    ): TokenRefreshResult {
        beforeReturn()
        return result
    }
}

private class FakeSessionStore(initialCredentials: SessionCredentials) : SessionStore {
    private val mutableSession =
        MutableStateFlow<LocalSession>(LocalSession.Authenticated(initialCredentials))
    private val mutableSessionState =
        MutableStateFlow<UserSessionState>(UserSessionState.Authenticated(initialCredentials.userId))

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

    override suspend fun requireReauthentication(userId: String) {
        val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials ?: return
        if (current.userId != userId) return
        mutableSession.value = LocalSession.ReauthenticationRequired(userId)
        mutableSessionState.value = UserSessionState.ReauthenticationRequired(userId)
    }
}
