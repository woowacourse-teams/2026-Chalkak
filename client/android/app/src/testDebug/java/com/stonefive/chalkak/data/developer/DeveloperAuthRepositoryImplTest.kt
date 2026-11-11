package com.stonefive.chalkak.data.developer

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.AuthTokenRefresher
import com.stonefive.chalkak.data.remote.AuthorizationRequestContext
import com.stonefive.chalkak.data.remote.SessionRefreshCoordinator
import com.stonefive.chalkak.data.remote.TokenAuthenticator
import com.stonefive.chalkak.data.remote.auth.RefreshApi
import com.stonefive.chalkak.domain.developer.ConcurrentRequestReport
import com.stonefive.chalkak.domain.developer.RefreshTokenRevokeResult
import com.stonefive.chalkak.domain.model.UserSessionState
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DeveloperAuthRepositoryImplTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val store = FakeSessionStore(credentials(accessToken = "at-1", refreshToken = "rt-1"))
    private val probe = RefreshRequestProbe()
    private val fakeServer = FakeAuthServer()
    private lateinit var authenticatedClient: OkHttpClient
    private lateinit var repository: DeveloperAuthRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = fakeServer
        server.start()
        authenticatedClient = authenticatedClient()
        repository = DeveloperAuthRepositoryImpl(
            sessionStore = store,
            authenticatedClient = authenticatedClient,
            baseUrl = server.url("/api/v1/"),
            refreshRequestProbe = probe,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Access 토큰 강제 만료를 누르면 저장된 AT만 무효 값으로 바뀐다`() = runBlocking {
        // Given "u1", AT "at-1", RT "rt-1"
        // When  AT 강제 만료
        // Then  AT는 "at-1"이 아닌 무효 값, RT "rt-1", 세션 상태 Authenticated("u1")
        val expired = repository.expireAccessToken()

        assertTrue(expired)
        assertNotEquals("at-1", store.credentials()?.accessToken)
        assertTrue(
            store
                .credentials()
                ?.accessToken
                .orEmpty()
                .isNotBlank(),
        )
        assertEquals("rt-1", store.credentials()?.refreshToken)
        assertEquals(UserSessionState.Authenticated("u1"), store.sessionState.value)
    }

    @Test
    fun `Refresh 토큰 폐기를 누르면 현재 RT로 로그아웃 요청을 보내고 AT를 무효로 바꾼다`() = runBlocking {
        // Given "u1", AT "at-1", RT "rt-1"
        // When  Refresh 토큰 폐기
        // Then  logout 요청 1회(본문 refreshToken "rt-1"), 로컬 RT "rt-1" 유지, AT 무효 값, 세션 상태 Authenticated("u1")
        val result = repository.revokeRefreshToken()

        assertEquals(RefreshTokenRevokeResult.Success, result)
        assertEquals(1, fakeServer.logoutBodies.size)
        assertEquals(
            "rt-1",
            json
                .parseToJsonElement(fakeServer.logoutBodies.single())
                .jsonObject["refreshToken"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("rt-1", store.credentials()?.refreshToken)
        assertNotEquals("at-1", store.credentials()?.accessToken)
        assertEquals(UserSessionState.Authenticated("u1"), store.sessionState.value)
    }

    @Test
    fun `Refresh 토큰 폐기 요청이 실패하면 토큰을 바꾸지 않고 실패를 알린다`() = runBlocking {
        // Given logout 요청이 500
        // When  Refresh 토큰 폐기
        // Then  AT "at-1" 그대로, 결과 실패(500) (문구 "폐기 요청 실패 (500)"는 DeveloperToolsViewModelTest에서 본다)
        fakeServer.logoutCode = 500

        val result = repository.revokeRefreshToken()

        assertEquals(RefreshTokenRevokeResult.Failure(500), result)
        assertEquals("at-1", store.credentials()?.accessToken)
    }

    @Test
    fun `Refresh 토큰 폐기(30초 뒤 만료)를 누르면 지금은 로그아웃 요청만 보내고 AT는 30초 뒤 무효로 바뀐다`() = runTest {
        // Given "u1", AT "at-1", RT "rt-1"
        // When  Refresh 토큰 폐기(30초 뒤 만료)
        // Then  logout 요청 1회(본문 refreshToken "rt-1"), 바로는 AT "at-1" 그대로, 30초 뒤 AT 무효 값
        //       (결과 문구는 DeveloperToolsViewModelTest에서 본다)
        val delayedRepository = repositoryScheduledIn(backgroundScope)

        val result = delayedRepository.revokeRefreshTokenAndExpireAccessTokenAfter(30.seconds)

        assertEquals(RefreshTokenRevokeResult.Success, result)
        assertEquals(listOf("rt-1"), fakeServer.logoutBodies.map(::refreshTokenOf))
        assertEquals("at-1", store.credentials()?.accessToken)
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals("at-1", store.credentials()?.accessToken)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertNotEquals("at-1", store.credentials()?.accessToken)
        assertTrue(
            store
                .credentials()
                ?.accessToken
                .orEmpty()
                .isNotBlank(),
        )
        assertEquals("rt-1", store.credentials()?.refreshToken)
        assertEquals(UserSessionState.Authenticated("u1"), store.sessionState.value)
    }

    @Test
    fun `30초가 되기 전에 다른 계정으로 바뀌면 AT를 바꾸지 않는다`() = runTest {
        // Given "u1"로 폐기(30초 뒤 만료)를 누른 뒤 10초에 "u2"로 로그인(AT "at-2")
        // When  30초가 지난다
        // Then  AT "at-2" 그대로, 세션 상태 Authenticated("u2")
        val delayedRepository = repositoryScheduledIn(backgroundScope)
        delayedRepository.revokeRefreshTokenAndExpireAccessTokenAfter(30.seconds)
        advanceTimeBy(10.seconds)
        store.saveSession(credentials(accessToken = "at-2", refreshToken = "rt-2", userId = "u2"))

        advanceTimeBy(21.seconds)
        runCurrent()

        assertEquals("at-2", store.credentials()?.accessToken)
        assertEquals(UserSessionState.Authenticated("u2"), store.sessionState.value)
    }

    @Test
    fun `동시 요청 5개를 보내면 갱신 횟수와 성공 수를 보여 준다`() = runBlocking {
        // Given AT 무효, 서버는 무효 AT에 401·갱신 요청에 새 토큰
        // When  동시 요청 5개
        // Then  갱신 요청 1회, 성공 5 (화면 문구 "갱신 1회 · 성공 5/5"는 DeveloperToolsViewModelTest에서 본다)
        repository.expireAccessToken()

        val report = repository.sendConcurrentRequests(5)

        assertEquals(ConcurrentRequestReport(refreshCount = 1, successCount = 5, requestCount = 5), report)
        assertEquals(1, fakeServer.refreshRequests.get())
    }

    @Test
    fun `네트워크 실패 스위치가 켜져 있으면 갱신 요청이 네트워크 오류로 끝나고 로그인은 유지된다`() = runBlocking {
        // Given 스위치 켬, AT 무효
        // When  일반 API 요청
        // Then  서버 갱신 요청 0회, 원요청 IOException, 세션 상태 Authenticated("u1")
        repository.setRefreshNetworkFailureEnabled(true)
        repository.expireAccessToken()

        val outcome = runCatching {
            authenticatedClient
                .newCall(
                    Request
                        .Builder()
                        .url(server.url("/api/v1/users/me/signature"))
                        .build(),
                ).execute()
                .use { it.code }
        }

        assertEquals(0, fakeServer.refreshRequests.get())
        assertTrue("IOException이어야 함: $outcome", outcome.exceptionOrNull() is IOException)
        assertEquals(UserSessionState.Authenticated("u1"), store.sessionState.value)
    }

    // 30초 타이머를 가상 시간으로 돌리려고 예약 범위만 테스트 범위로 바꾼다.
    private fun repositoryScheduledIn(scope: CoroutineScope) = DeveloperAuthRepositoryImpl(
        sessionStore = store,
        authenticatedClient = authenticatedClient,
        baseUrl = server.url("/api/v1/"),
        refreshRequestProbe = probe,
        scheduleScope = scope,
    )

    private fun refreshTokenOf(body: String): String? = json
        .parseToJsonElement(body)
        .jsonObject["refreshToken"]
        ?.jsonPrimitive
        ?.content

    // NetworkModule과 같은 순서로 붙인다. 실제 AuthorizationHeaderInterceptor는 https가 아니면 토큰을 빼므로
    // MockWebServer(http)에서는 같은 일을 하는 테스트용 인터셉터를 쓴다.
    private fun authenticatedClient(): OkHttpClient {
        val refreshApi = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(
                OkHttpClient
                    .Builder()
                    .addInterceptor(probe)
                    .build(),
            ).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RefreshApi::class.java)
        val coordinator = SessionRefreshCoordinator(store, AuthTokenRefresher(refreshApi, json))
        return OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val credentials = store.credentials()
                val request = if (credentials == null) {
                    chain.request()
                } else {
                    chain
                        .request()
                        .newBuilder()
                        .header("Authorization", "Bearer ${credentials.accessToken}")
                        .tag(
                            AuthorizationRequestContext::class.java,
                            AuthorizationRequestContext(credentials.accessToken, credentials.userId),
                        ).build()
                }
                chain.proceed(request)
            }.authenticator(TokenAuthenticator(store, coordinator, json))
            .build()
    }
}

/** 갱신·로그아웃·로그인 필요 API를 흉내 낸다. "new-at"만 유효한 Access 토큰으로 본다. */
private class FakeAuthServer : Dispatcher() {
    val refreshRequests = AtomicInteger(0)
    val logoutBodies = java.util.concurrent
        .CopyOnWriteArrayList<String>()

    @Volatile var logoutCode = 204

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/api/v1/auth/refresh" -> {
            refreshRequests.incrementAndGet()
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"accessToken":"at-valid","expiresIn":900,"refreshToken":"rt-2","refreshTokenExpiresIn":2592000}""",
                )
        }

        "/api/v1/auth/logout" -> {
            logoutBodies += request.body.readUtf8()
            MockResponse().setResponseCode(logoutCode)
        }

        else -> if (request.headers["Authorization"] == "Bearer at-valid" ||
            request.headers["Authorization"] == "Bearer at-1"
        ) {
            MockResponse().setResponseCode(200).setBody("{}")
        } else {
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"UNAUTHORIZED"}""")
        }
    }
}

private fun credentials(
    accessToken: String,
    refreshToken: String,
    userId: String = "u1",
) = SessionCredentials(
    userId = userId,
    accessToken = accessToken,
    expiresAtEpochSeconds = Long.MAX_VALUE,
    refreshToken = refreshToken,
    refreshTokenExpiresAtEpochSeconds = Long.MAX_VALUE,
)

private class FakeSessionStore(initial: SessionCredentials) : SessionStore {
    private val mutableSession = MutableStateFlow<LocalSession>(LocalSession.Authenticated(initial))
    private val mutableSessionState =
        MutableStateFlow<UserSessionState>(UserSessionState.Authenticated(initial.userId))

    override val session: StateFlow<LocalSession> = mutableSession
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    fun credentials(): SessionCredentials? = (mutableSession.value as? LocalSession.Authenticated)?.credentials

    override suspend fun continueAsGuest() = error("unused")

    override suspend fun saveSession(credentials: SessionCredentials) {
        mutableSession.value = LocalSession.Authenticated(credentials)
        mutableSessionState.value = UserSessionState.Authenticated(credentials.userId)
    }

    override suspend fun updateTokens(credentials: SessionCredentials): Boolean {
        if (credentials()?.userId != credentials.userId) return false
        saveSession(credentials)
        return true
    }

    override suspend fun clear() {
        mutableSession.value = LocalSession.SignedOut
        mutableSessionState.value = UserSessionState.SignedOut
    }

    override suspend fun requireReauthentication(userId: String) {
        mutableSession.value = LocalSession.ReauthenticationRequired(userId)
        mutableSessionState.value = UserSessionState.ReauthenticationRequired(userId)
    }
}
