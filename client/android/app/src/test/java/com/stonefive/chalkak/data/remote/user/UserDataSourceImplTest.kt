package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UserDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: UserDataSourceImpl
    private var unauthorizedHandled = false

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
            .create(UserApi::class.java)
        unauthorizedHandled = false
        dataSource = UserDataSourceImpl(
            api = api,
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
    fun `내 서명 조회 경로와 응답을 명세대로 처리한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"signatureOriginalImageUrl":"https://cdn.example.com/signatures/original.png","signatureThumbnailImageUrl":"https://cdn.example.com/signatures/thumbnail.png"}""",
                ),
        )

        val result = dataSource.getMySignature()
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                UserSignatureResponse(
                    originalImageUrl = "https://cdn.example.com/signatures/original.png",
                    thumbnailImageUrl = "https://cdn.example.com/signatures/thumbnail.png",
                ),
            ),
            result,
        )
        assertEquals("GET", request.method)
        assertEquals("/api/v1/users/me/signature", request.path)
    }

    @Test
    fun `내 서명 조회의 403 응답을 Http 오류로 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"서비스를 이용할 수 없는 계정입니다."}"""),
        )

        val result = dataSource.getMySignature()

        assertEquals(
            ApiResult.Failure(ApiError.Http(statusCode = 403, errorCode = null)),
            result,
        )
        assertEquals(false, unauthorizedHandled)
    }

    @Test
    fun `내 서명 조회가 401이면 세션 무효화 처리를 호출한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"errorCode":"UNAUTHORIZED","message":"인증 정보가 유효하지 않습니다."}""",
                ),
        )

        val result = dataSource.getMySignature()

        assertEquals(
            ApiResult.Failure(ApiError.Http(statusCode = 401, errorCode = "UNAUTHORIZED")),
            result,
        )
        assertEquals(true, unauthorizedHandled)
    }

    @Test
    fun `사인 업로드 URL 발급 경로와 응답을 명세대로 처리한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"uploadId":"upload-id","uploadUrl":"https://cdn.example.com/upload","expiresInSeconds":300}""",
                ),
        )

        val result = dataSource.createSignatureUpload()
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                SignatureUploadResponse(
                    uploadId = "upload-id",
                    uploadUrl = "https://cdn.example.com/upload",
                    expiresInSeconds = 300,
                ),
            ),
            result,
        )
        assertEquals("POST", request.method)
        assertEquals("/api/v1/users/me/signature/uploads", request.path)
    }

    @Test
    fun `사인 변경 확정은 업로드 ID를 JSON으로 전송한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"signatureOriginalImageUrl":"https://cdn.example.com/signatures/new.png"}""",
                ),
        )

        val result = dataSource.updateSignature("upload-id")
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                SignatureUpdateResponse(
                    originalImageUrl = "https://cdn.example.com/signatures/new.png",
                ),
            ),
            result,
        )
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/users/me/signature", request.path)
        assertEquals(
            "{\"signatureOriginalUploadId\":\"upload-id\"}",
            request.body.readUtf8(),
        )
    }
}
