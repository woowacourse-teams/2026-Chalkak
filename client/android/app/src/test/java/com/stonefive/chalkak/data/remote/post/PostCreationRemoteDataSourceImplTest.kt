package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostCreateResponse
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import com.stonefive.chalkak.data.remote.topic.TopicApi
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate
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

class PostCreationRemoteDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: PostCreationRemoteDataSourceImpl
    private var unauthorizedHandled = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        unauthorizedHandled = false
        dataSource = PostCreationRemoteDataSourceImpl(
            postApi = retrofit.create(PostApi::class.java),
            topicApi = retrofit.create(TopicApi::class.java),
            requestExecutor = ApiRequestExecutor(json) {
                unauthorizedHandled = true
            },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `오늘 주제를 날짜 query로 조회한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"topic-id","title":"바다","topicDate":"2026-08-29"}"""),
        )

        val result = dataSource.getTopic(LocalDate.of(2026, 8, 29))
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                TopicResponse(
                    id = "topic-id",
                    title = "바다",
                    topicDate = "2026-08-29",
                ),
            ),
            result,
        )
        assertEquals("GET", request.method)
        assertEquals("/api/v1/topics?date=2026-08-29", request.path)
    }

    @Test
    fun `업로드 정책 응답의 Long maxBytes를 보존한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"uploadId":"upload-id","uploadUrl":"https://s3.example/upload","expiresInSeconds":300,"contentType":"image/webp","maxBytes":9223372036854770000}""",
                ),
        )

        val result = dataSource.createPostImageUpload()
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                PostImageUploadResponse(
                    uploadId = "upload-id",
                    uploadUrl = "https://s3.example/upload",
                    expiresInSeconds = 300,
                    contentType = "image/webp",
                    maxBytes = 9_223_372_036_854_770_000L,
                ),
            ),
            result,
        )
        assertEquals("POST", request.method)
        assertEquals("/api/v1/posts/uploads", request.path)
    }

    @Test
    fun `게시물 생성은 topic upload title을 JSON으로 전송한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"postId":"post-id","moderationStatus":"PENDING"}"""),
        )

        val result = dataSource.createPost(
            topicId = "topic-id",
            photoUploadId = "upload-id",
            title = "📸 바다",
        )
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(PostCreateResponse("post-id", "PENDING")),
            result,
        )
        assertEquals("POST", request.method)
        assertEquals("/api/v1/posts", request.path)
        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\",\"title\":\"📸 바다\"}",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `제목이 null이면 선택 field를 생략한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"postId":"post-id","moderationStatus":"VALIDATING"}"""),
        )

        dataSource.createPost(
            topicId = "topic-id",
            photoUploadId = "upload-id",
            title = null,
        )

        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\"}",
            server
                .takeRequest()
                .body
                .readUtf8(),
        )
    }

    @Test
    fun `401 응답은 세션 무효화와 Http 오류로 변환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"UNAUTHORIZED","message":"unauthorized"}"""),
        )

        val result = dataSource.createPostImageUpload()

        assertEquals(
            ApiResult.Failure(ApiError.Http(401, "UNAUTHORIZED", "unauthorized")),
            result,
        )
        assertTrue(unauthorizedHandled)
    }

    @Test
    fun `게시물 생성 실패 응답의 errorCode와 message를 보존한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"errorCode":"BUSINESS_ERROR","message":"이미 해당 주제에 게시물을 작성했습니다."}""",
                ),
        )

        val result = dataSource.createPost("topic-id", "upload-id", null)

        assertEquals(
            ApiResult.Failure(
                ApiError.Http(
                    statusCode = 400,
                    errorCode = "BUSINESS_ERROR",
                    message = "이미 해당 주제에 게시물을 작성했습니다.",
                ),
            ),
            result,
        )
    }

    @Test
    fun `잘못된 성공 body는 InvalidResponse로 변환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not-json"),
        )

        assertEquals(
            ApiResult.Failure(ApiError.InvalidResponse),
            dataSource.createPostImageUpload(),
        )
    }
}
