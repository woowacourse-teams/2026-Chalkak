package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.AuthorizationRequestContext
import com.stonefive.chalkak.data.remote.post.model.PostCreateResponse
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PostCreationRemoteDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: PostCreationRemoteDataSourceImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain
                    .request()
                    .newBuilder()
                    .header("Authorization", "Bearer access-token")
                    .tag(
                        AuthorizationRequestContext::class.java,
                        AuthorizationRequestContext("access-token"),
                    ).build()
                chain.proceed(request)
            }.build()
        val retrofit = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        dataSource = PostCreationRemoteDataSourceImpl(
            postApi = retrofit.create(PostApi::class.java),
            requestExecutor = ApiRequestExecutor(json),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
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
            description = "퇴근길에\n만난 노을",
            instagramUploadAgreed = false,
            instagramId = null,
        )
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(PostCreateResponse("post-id", "PENDING")),
            result,
        )
        assertEquals("POST", request.method)
        assertEquals("/api/v1/posts", request.path)
        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\",\"title\":\"📸 바다\",\"description\":\"퇴근길에\\n만난 노을\"}",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `제목과 설명이 null이면 선택 field를 생략한다`() = runTest {
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
            description = null,
            instagramUploadAgreed = false,
            instagramId = null,
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
    fun `동의하지 않고 전시하면 동의 안 함으로 보낸다`() = runTest {
        // Given 스위치를 켜지 않은 작성 화면
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"postId":"post-id","moderationStatus":"VALIDATING"}"""),
        )

        // When  전시하기
        dataSource.createPost("topic-id", "upload-id", null, null, instagramUploadAgreed = false, instagramId = null)

        // Then  작성 요청에 동의 필드가 없다. 서버는 instagramUploadAgreed 생략을 false(동의 안 함), instagramId 생략을 null로 본다
        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\"}",
            server
                .takeRequest()
                .body
                .readUtf8(),
        )
    }

    @Test
    fun `동의하고 ID를 쓰면 ID와 함께 보낸다`() = runTest {
        // Given 스위치를 켜고 ID에 "chalkak.daily" 입력
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"postId":"post-id","moderationStatus":"VALIDATING"}"""),
        )

        // When  전시하기
        dataSource.createPost(
            "topic-id",
            "upload-id",
            null,
            null,
            instagramUploadAgreed = true,
            instagramId = "chalkak.daily",
        )

        // Then  작성 요청의 instagramUploadAgreed가 true, instagramId가 "chalkak.daily"
        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\",\"instagramUploadAgreed\":true,\"instagramId\":\"chalkak.daily\"}",
            server
                .takeRequest()
                .body
                .readUtf8(),
        )
    }

    @Test
    fun `동의하고 ID를 비우면 ID 없이 보낸다`() = runTest {
        // Given 스위치를 켜고 ID 칸이 공백뿐(앱이 null로 바꿔 넘긴다)
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"postId":"post-id","moderationStatus":"VALIDATING"}"""),
        )

        // When  전시하기
        dataSource.createPost("topic-id", "upload-id", null, null, instagramUploadAgreed = true, instagramId = null)

        // Then  작성 요청의 instagramUploadAgreed가 true, instagramId가 null(생략)
        assertEquals(
            "{\"topicId\":\"topic-id\",\"photoUploadId\":\"upload-id\",\"instagramUploadAgreed\":true}",
            server
                .takeRequest()
                .body
                .readUtf8(),
        )
    }

    @Test
    fun `401 응답을 Http 오류로 변환한다`() = runTest {
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

        val result = dataSource.createPost("topic-id", "upload-id", null, null, false, null)

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
