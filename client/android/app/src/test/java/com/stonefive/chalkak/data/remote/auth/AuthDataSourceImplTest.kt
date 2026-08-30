package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider
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

class AuthDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: AuthDataSourceImpl

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
            .create(AuthApi::class.java)
        dataSource = AuthDataSourceImpl(
            api = api,
            requestExecutor = ApiRequestExecutor(json) { _ -> },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `소셜 로그인 경로와 JSON body를 명세대로 전송한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"status":"LOGIN_SUCCESS","userId":"user-id","accessToken":"access-token","expiresIn":3600}""",
                ),
        )

        val result = dataSource.socialLogin(SocialLoginProvider.GOOGLE, "id-token")
        val request = server.takeRequest()

        assertEquals(
            ApiResult.Success(
                SocialLoginResponse.LoginSuccess(
                    userId = "user-id",
                    accessToken = "access-token",
                    expiresIn = 3600,
                ),
            ),
            result,
        )
        assertEquals("/api/v1/auth/social-login", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"provider\":\"GOOGLE\""))
        assertTrue(body.contains("\"idToken\":\"id-token\""))
    }

    @Test
    fun `Kakao 소셜 로그인 JSON body에는 Kakao provider와 idToken을 전송한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"SIGN_UP_REQUIRED"}"""),
        )

        val result = dataSource.socialLogin(SocialLoginProvider.KAKAO, "kakao-id-token")
        val body = server
            .takeRequest()
            .body
            .readUtf8()

        assertEquals(ApiResult.Success(SocialLoginResponse.SignUpRequired), result)
        assertTrue(body.contains("\"provider\":\"KAKAO\""))
        assertTrue(body.contains("\"idToken\":\"kakao-id-token\""))
    }

    @Test
    fun `로그인 성공 응답에 필수 인증 정보가 없으면 잘못된 응답으로 처리한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"LOGIN_SUCCESS","userId":"user-id","expiresIn":3600}"""),
        )

        val result = dataSource.socialLogin(SocialLoginProvider.GOOGLE, "id-token")

        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), result)
    }

    @Test
    fun `서명 업로드 응답에서 회원가입 토큰을 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"uploadId":"upload-id","uploadUrl":"https://example.com/upload","expiresInSeconds":300,"signupToken":"signup-token","signupTokenExpiresInSeconds":1800}""",
                ),
        )

        val result = dataSource.createSignatureUpload(
            provider = SocialLoginProvider.GOOGLE,
            idToken = "id-token",
        )

        assertEquals(
            ApiResult.Success(
                SignatureUploadResponse(
                    uploadId = "upload-id",
                    uploadUrl = "https://example.com/upload",
                    expiresInSeconds = 300,
                    signupToken = "signup-token",
                    signupTokenExpiresInSeconds = 1_800,
                ),
            ),
            result,
        )
    }

    @Test
    fun `에러 body의 errorCode를 구조화해서 반환한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"errorCode":"SIGNATURE_PROCESSING_PENDING","message":"processing"}""",
                ),
        )

        val result = dataSource.socialSignUp(signupToken = "signup-token")

        val body = server
            .takeRequest()
            .body
            .readUtf8()

        assertEquals(
            ApiResult.Failure(
                ApiError.Http(
                    statusCode = 400,
                    errorCode = "SIGNATURE_PROCESSING_PENDING",
                ),
            ),
            result,
        )
        assertEquals("""{"signupToken":"signup-token"}""", body)
    }
}
