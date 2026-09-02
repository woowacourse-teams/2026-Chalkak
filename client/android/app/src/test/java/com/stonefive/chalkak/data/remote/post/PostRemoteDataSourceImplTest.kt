package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.AuthorizationRequestContext
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PostRemoteDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: PostRemoteDataSourceImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = createDataSource()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `게시물 상세 요청은 postId를 path에 포함한다`() = runTest {
        server.enqueue(jsonResponse(DETAIL_BODY))

        val result = dataSource.getPostDetail(POST_ID)

        assertTrue(result is ApiResult.Success<*>)
        assertEquals("/api/v1/posts/$POST_ID", server.takeRequest().path)
    }

    @Test
    fun `게시물 삭제는 postId 경로로 DELETE하고 204를 성공 처리한다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = dataSource.deletePost(POST_ID)
        val request = server.takeRequest()

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/posts/$POST_ID", request.path)
    }

    @Test
    fun `게시물 캘린더 요청은 조회 연월을 query로 전송한다`() = runTest {
        server.enqueue(jsonResponse(CALENDAR_BODY))

        val result = dataSource.getPostCalendar(YearMonth.of(2026, 8))

        assertTrue(result is ApiResult.Success<*>)
        assertEquals("/api/v1/posts/calendar?year=2026&month=8", server.takeRequest().path)
    }

    @Test
    fun `정렬 값과 페이지 계약을 쿼리에 직렬화한다`() = runTest {
        listOf(
            PostSort.LATEST to "recent",
            PostSort.POPULAR to "popular",
            PostSort.RANDOM to "random",
        ).forEach { (sort, expectedValue) ->
            server.enqueue(jsonResponse(POSTS_BODY))

            dataSource.getPosts(
                HomeQuery(
                    date = LocalDate.of(2026, 8, 28),
                    sort = sort,
                    page = 1,
                ),
            )

            val requestUrl = checkNotNull(server.takeRequest().requestUrl)
            assertEquals("2026-08-28", requestUrl.queryParameter("topicDate"))
            assertEquals(expectedValue, requestUrl.queryParameter("sort"))
            assertEquals("1", requestUrl.queryParameter("page"))
            assertEquals("20", requestUrl.queryParameter("pageSize"))
        }
    }

    @Test
    fun `랜덤 첫 페이지는 seed를 생략하고 응답 seed를 보존한다`() = runTest {
        server.enqueue(jsonResponse(POSTS_BODY))

        val result = dataSource.getPosts(
            HomeQuery(
                date = LocalDate.of(2026, 8, 28),
                sort = PostSort.RANDOM,
                page = 1,
            ),
        )

        val requestUrl = server
            .takeRequest()
            .requestUrl
        assertNull(requestUrl?.queryParameter("randomSeed"))
        val page = (result as ApiResult.Success<PostPageResponse>).value
        assertEquals("seed-1", page.randomSeed)
    }

    @Test
    fun `랜덤 다음 페이지는 첫 응답의 정확한 seed를 전송한다`() = runTest {
        server.enqueue(jsonResponse(POSTS_BODY))

        dataSource.getPosts(
            HomeQuery(
                date = LocalDate.of(2026, 8, 28),
                sort = PostSort.RANDOM,
                page = 2,
                randomSeed = "seed-1",
            ),
        )

        val requestUrl = server
            .takeRequest()
            .requestUrl
        assertEquals("seed-1", requestUrl?.queryParameter("randomSeed"))
    }

    @Test
    fun `랜덤이 아닌 요청은 seed를 전송하지 않는다`() = runTest {
        server.enqueue(jsonResponse(POSTS_BODY))

        dataSource.getPosts(
            HomeQuery(
                date = LocalDate.of(2026, 8, 28),
                sort = PostSort.POPULAR,
                page = 2,
            ),
        )

        val requestUrl = server
            .takeRequest()
            .requestUrl
        assertNull(requestUrl?.queryParameter("randomSeed"))
    }

    @Test
    fun `좋아요는 PUT이고 좋아요 취소는 DELETE다`() = runTest {
        server.enqueue(jsonResponse(LIKE_BODY))
        server.enqueue(jsonResponse(LIKE_BODY.replace("true", "false")))

        dataSource.updateLike(POST_ID, isLiked = true)
        dataSource.updateLike(POST_ID, isLiked = false)

        assertEquals("PUT", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `인증된 게시물 요청은 Authorization bearer 헤더를 사용한다`() = runTest {
        val sessionStore = TestSessionStore(
            initialState = UserSessionState.Authenticated("user-id"),
            initialAccessToken = "access-token",
        )
        dataSource = createDataSource(
            client = OkHttpClient
                .Builder()
                .addInterceptor(testAuthorizationInterceptor("access-token"))
                .build(),
            sessionStore = sessionStore,
        )
        server.enqueue(jsonResponse(POSTS_BODY))

        dataSource.getPosts(
            HomeQuery(
                date = LocalDate.of(2026, 8, 28),
                sort = PostSort.LATEST,
                page = 1,
            ),
        )

        val request = server.takeRequest()
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertNull(request.headers["X-User-Id"])
    }

    @Test
    fun `Post API가 401이면 세션을 무효화한다`() = runTest {
        val sessionStore = TestSessionStore(
            initialState = UserSessionState.Authenticated("user-id"),
            initialAccessToken = "access-token",
        )
        dataSource = createDataSource(
            client = OkHttpClient
                .Builder()
                .addInterceptor(testAuthorizationInterceptor("access-token"))
                .build(),
            sessionStore = sessionStore,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"UNAUTHORIZED"}"""),
        )

        val result = dataSource.getPostCalendar(YearMonth.of(2026, 8))

        assertEquals(ApiResult.Failure(ApiError.Http(401, "UNAUTHORIZED")), result)
        assertEquals(UserSessionState.SignedOut, sessionStore.sessionState.value)
        assertEquals(LocalSession.SignedOut, sessionStore.session.value)
    }

    @Test
    fun `이전 access token 요청의 401은 새 세션을 무효화하지 않는다`() = runTest {
        val sessionStore = TestSessionStore(
            initialState = UserSessionState.Authenticated("user-id"),
            initialAccessToken = "old-access-token",
        )
        val newCredentials = SessionCredentials(
            userId = "user-id",
            accessToken = "new-access-token",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        )
        dataSource = createDataSource(
            client = OkHttpClient
                .Builder()
                .addInterceptor(testAuthorizationInterceptor("old-access-token"))
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    runBlocking { sessionStore.saveSession(newCredentials) }
                    response
                }.build(),
            sessionStore = sessionStore,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"UNAUTHORIZED"}"""),
        )

        dataSource.getPostCalendar(YearMonth.of(2026, 8))

        assertEquals(LocalSession.Authenticated(newCredentials), sessionStore.session.value)
    }

    @Test
    fun `HTTP 실패 상태를 보존한다`() = runTest {
        listOf(404, 401, 500, 400).forEach { statusCode ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(statusCode)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"errorCode":"ERROR_$statusCode"}"""),
            )

            val result = dataSource.getPostCalendar(YearMonth.of(2026, 8))

            assertEquals(
                ApiResult.Failure(ApiError.Http(statusCode, "ERROR_$statusCode")),
                result,
            )
        }
    }

    @Test
    fun `빈 성공 body와 깨진 JSON은 invalid response다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(jsonResponse("{"))

        assertEquals(
            ApiResult.Failure(ApiError.InvalidResponse),
            dataSource.getPostCalendar(YearMonth.of(2026, 8)),
        )
        assertEquals(
            ApiResult.Failure(ApiError.InvalidResponse),
            dataSource.getPostCalendar(YearMonth.of(2026, 8)),
        )
    }

    @Test
    fun `사인 썸네일이 누락되거나 null이면 invalid response다`() = runTest {
        listOf(
            POSTS_BODY.replace(
                "\"signatureThumbnailImageUrl\":\"https://example.com/signature-thumbnail.png\",",
                "",
            ),
            POSTS_BODY.replace(
                "\"signatureThumbnailImageUrl\":\"https://example.com/signature-thumbnail.png\"",
                "\"signatureThumbnailImageUrl\":null",
            ),
        ).forEach { body ->
            server.enqueue(jsonResponse(body))

            assertEquals(
                ApiResult.Failure(ApiError.InvalidResponse),
                dataSource.getPosts(
                    HomeQuery(
                        date = LocalDate.of(2026, 8, 28),
                        sort = PostSort.LATEST,
                        page = 1,
                    ),
                ),
            )
        }
    }

    @Test
    fun `게시물 썸네일이 누락되거나 null이면 invalid response다`() = runTest {
        listOf(
            POSTS_BODY.replace(
                "\"thumbnailImageUrl\":\"https://example.com/thumbnail.jpg\",",
                "",
            ),
            POSTS_BODY.replace(
                "\"thumbnailImageUrl\":\"https://example.com/thumbnail.jpg\"",
                "\"thumbnailImageUrl\":null",
            ),
        ).forEach { body ->
            server.enqueue(jsonResponse(body))

            assertEquals(
                ApiResult.Failure(ApiError.InvalidResponse),
                dataSource.getPosts(
                    HomeQuery(
                        date = LocalDate.of(2026, 8, 28),
                        sort = PostSort.LATEST,
                        page = 1,
                    ),
                ),
            )
        }
    }

    @Test
    fun `서버 연결 실패는 network failure다`() = runTest {
        server.shutdown()

        val result = dataSource.getPostCalendar(YearMonth.of(2026, 8))

        assertEquals(ApiResult.Failure(ApiError.Network), result)
    }

    private fun createDataSource(
        client: OkHttpClient = OkHttpClient(),
        sessionStore: TestSessionStore = TestSessionStore(UserSessionState.SignedOut),
    ): PostRemoteDataSourceImpl {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return PostRemoteDataSourceImpl(
            postApi = retrofit.create(PostApi::class.java),
            requestExecutor = ApiRequestExecutor(json, sessionStore::clearIfAccessTokenMatches),
        )
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val POST_ID = "11111111-1111-1111-1111-111111111111"
        const val POSTS_BODY =
            """{"currentPage":1,"pageSize":20,"hasNext":true,"randomSeed":"seed-1","posts":[{"id":"$POST_ID","originalImageUrl":"https://example.com/original.jpg","thumbnailImageUrl":"https://example.com/thumbnail.jpg","signatureOriginalImageUrl":"https://example.com/signature.png","signatureThumbnailImageUrl":"https://example.com/signature-thumbnail.png","title":"바다 사진","submittedAt":"2026-08-28T01:00:00Z","likeCount":3,"isLiked":false}]}"""
        const val DETAIL_BODY =
            """{"id":"$POST_ID","topic":{"id":"topic-id","title":"바다","topicDate":"2026-08-28"},"originalImageUrl":"https://example.com/original.jpg","thumbnailImageUrl":"https://example.com/thumbnail.jpg","signatureOriginalImageUrl":"https://example.com/signature.png","title":"바다 사진","likeCount":3,"isLiked":false}"""
        const val LIKE_BODY =
            """{"postId":"$POST_ID","likeCount":4,"isLiked":true}"""
        const val CALENDAR_BODY =
            """{"year":2026,"month":8,"posts":[{"topicDate":"2026-08-31","postId":"$POST_ID","thumbnailImageUrl":"https://example.com/thumbnail.jpg","status":"APPROVED"}]}"""
    }
}

private fun testAuthorizationInterceptor(accessToken: String) = Interceptor { chain ->
    val request = chain
        .request()
        .newBuilder()
        .header("Authorization", "Bearer $accessToken")
        .tag(
            AuthorizationRequestContext::class.java,
            AuthorizationRequestContext(accessToken),
        ).build()
    chain.proceed(request)
}

private class TestSessionStore(
    initialState: UserSessionState,
    initialAccessToken: String? = null,
) : SessionStore {
    private val mutableSessionState = MutableStateFlow(initialState)
    private val mutableSession = MutableStateFlow<LocalSession>(
        when (initialState) {
            UserSessionState.Loading -> LocalSession.Loading

            UserSessionState.SignedOut -> LocalSession.SignedOut

            UserSessionState.Guest -> LocalSession.Guest

            is UserSessionState.Authenticated -> LocalSession.Authenticated(
                SessionCredentials(
                    userId = initialState.userId,
                    accessToken = requireNotNull(initialAccessToken),
                    expiresAtEpochSeconds = Long.MAX_VALUE,
                ),
            )
        },
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

    override suspend fun clear() {
        mutableSession.value = LocalSession.SignedOut
        mutableSessionState.value = UserSessionState.SignedOut
    }

    override suspend fun clearIfAccessTokenMatches(accessToken: String) {
        val currentToken = (mutableSession.value as? LocalSession.Authenticated)?.credentials?.accessToken
        if (currentToken == accessToken) clear()
    }
}
