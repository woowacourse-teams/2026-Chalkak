package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.AuthDataSource
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import com.stonefive.chalkak.data.remote.signature.SignatureUploadResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploader
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpFailure
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryImplTest {
    private val authDataSource = FakeAuthDataSource()
    private val uploader = FakeSignatureUploader()
    private val sessionStore = FakeSessionStore()
    private val retryDelays = mutableListOf<Long>()
    private val repository = AuthRepositoryImpl(
        authDataSource = authDataSource,
        signatureUploader = uploader,
        sessionStore = sessionStore,
        retryDelay = retryDelays::add,
        currentEpochSeconds = { CURRENT_EPOCH_SECONDS },
    )

    @Test
    fun `기존 회원 로그인 성공 시 userId를 저장한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.LoginSuccess(
                userId = "user-id",
                accessToken = "access-token",
                expiresIn = 3_600,
            ),
        )

        val result = repository.login(SocialLoginProvider.GOOGLE, "id-token")

        assertEquals(SocialLoginResult.LoginSuccess("user-id"), result)
        assertEquals(SocialLoginProvider.GOOGLE, authDataSource.loginProvider)
        assertEquals("id-token", authDataSource.loginIdToken)
        assertEquals(UserSessionState.Authenticated("user-id"), sessionStore.sessionState.value)
        assertEquals(
            LocalSession.Authenticated(
                SessionCredentials(
                    userId = "user-id",
                    accessToken = "access-token",
                    expiresAtEpochSeconds = CURRENT_EPOCH_SECONDS + 3_600,
                ),
            ),
            sessionStore.session.value,
        )
    }

    @Test
    fun `Kakao 로그인 성공 시 Kakao provider와 idToken을 data source로 전달한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.LoginSuccess(
                userId = "user-id",
                accessToken = "access-token",
                expiresIn = 3_600,
            ),
        )

        val result = repository.login(SocialLoginProvider.KAKAO, "kakao-id-token")

        assertEquals(SocialLoginResult.LoginSuccess("user-id"), result)
        assertEquals(SocialLoginProvider.KAKAO, authDataSource.loginProvider)
        assertEquals("kakao-id-token", authDataSource.loginIdToken)
    }

    @Test
    fun `Kakao 신규 회원은 보류된 Kakao provider와 idToken으로 서명 업로드를 생성한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.SignUpRequired,
        )
        authDataSource.signUpResults += ApiResult.Success(
            SocialSignUpResponse("new-user-id"),
        )
        authDataSource.postSignUpLoginResult = ApiResult.Success(
            SocialLoginResponse.LoginSuccess(
                userId = "new-user-id",
                accessToken = "access-token",
                expiresIn = 3_600,
            ),
        )

        repository.login(SocialLoginProvider.KAKAO, "kakao-id-token")
        repository.completeSocialSignUp(byteArrayOf(1))

        assertEquals(SocialLoginProvider.KAKAO, authDataSource.createUploadProvider)
        assertEquals("kakao-id-token", authDataSource.createUploadIdToken)
    }

    @Test
    fun `신규 회원은 사인을 한 번 업로드하고 처리 중 응답에는 가입 완료만 재시도한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.SignUpRequired,
        )
        authDataSource.signUpResults += ApiResult.Failure(
            ApiError.Http(400, "SIGNATURE_PROCESSING_PENDING"),
        )
        authDataSource.signUpResults += ApiResult.Success(
            SocialSignUpResponse("new-user-id"),
        )
        authDataSource.postSignUpLoginResult = ApiResult.Success(
            SocialLoginResponse.LoginSuccess(
                userId = "new-user-id",
                accessToken = "access-token",
                expiresIn = 3_600,
            ),
        )
        val signaturePng = byteArrayOf(1, 2, 3)

        repository.login(SocialLoginProvider.GOOGLE, "id-token")
        val result = repository.completeSocialSignUp(signaturePng)

        assertEquals(SocialSignUpResult.Success("new-user-id"), result)
        assertEquals(1, authDataSource.createUploadCount)
        assertEquals(1, uploader.uploadCount)
        assertArrayEquals(signaturePng, uploader.uploadedPng)
        assertEquals(2, authDataSource.signUpCount)
        assertEquals(listOf(1_000L), retryDelays)
        assertEquals(listOf("signup-token", "signup-token"), authDataSource.signupTokens)
        assertEquals(UserSessionState.Authenticated("new-user-id"), sessionStore.sessionState.value)
        assertEquals(
            CURRENT_EPOCH_SECONDS + 3_600,
            (sessionStore.session.value as LocalSession.Authenticated)
                .credentials
                .expiresAtEpochSeconds,
        )
    }

    @Test
    fun `처리 중 응답이 열 번 계속되면 타임아웃을 반환한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.SignUpRequired,
        )
        repeat(10) {
            authDataSource.signUpResults += ApiResult.Failure(
                ApiError.Http(400, "SIGNATURE_PROCESSING_PENDING"),
            )
        }

        repository.login(SocialLoginProvider.GOOGLE, "id-token")
        val result = repository.completeSocialSignUp(byteArrayOf(1))

        assertEquals(
            SocialSignUpResult.Failure(SocialSignUpFailure.SIGNATURE_PROCESSING_TIMEOUT),
            result,
        )
        assertEquals(1, uploader.uploadCount)
        assertEquals(10, authDataSource.signUpCount)
        assertEquals(9, retryDelays.size)
    }

    @Test
    fun `잘못된 서명 업로드 URL은 재시도 가능한 가입 실패로 변환한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(
            SocialLoginResponse.SignUpRequired,
        )
        uploader.result = SignatureUploadResult.InvalidUploadUrl

        repository.login(SocialLoginProvider.GOOGLE, "id-token")
        val result = repository.completeSocialSignUp(byteArrayOf(1))

        assertEquals(
            SocialSignUpResult.Failure(SocialSignUpFailure.UNKNOWN),
            result,
        )
        assertEquals(1, uploader.uploadCount)
        assertEquals(0, authDataSource.signUpCount)
    }

    @Test
    fun `비회원으로 계속하면 게스트 세션으로 전환한다`() = runTest {
        repository.continueAsGuest()

        assertEquals(UserSessionState.Guest, repository.sessionState.value)
    }

    @Test
    fun `회원가입 성공 후 재인증 실패 시 보류된 가입 컨텍스트를 제거한다`() = runTest {
        authDataSource.loginResult = ApiResult.Success(SocialLoginResponse.SignUpRequired)
        authDataSource.signUpResults += ApiResult.Success(SocialSignUpResponse("new-user-id"))
        authDataSource.postSignUpLoginResult = ApiResult.Failure(ApiError.Network)

        repository.login(SocialLoginProvider.GOOGLE, "id-token")
        val firstResult = repository.completeSocialSignUp(byteArrayOf(1))
        val secondResult = repository.completeSocialSignUp(byteArrayOf(1))

        assertEquals(
            SocialSignUpResult.Failure(SocialSignUpFailure.NETWORK_UNAVAILABLE),
            firstResult,
        )
        assertEquals(
            SocialSignUpResult.Failure(SocialSignUpFailure.MISSING_LOGIN_CONTEXT),
            secondResult,
        )
        assertEquals(1, authDataSource.createUploadCount)
    }

    private companion object {
        const val CURRENT_EPOCH_SECONDS = 1_000_000L
    }
}

private class FakeAuthDataSource : AuthDataSource {
    var loginResult: ApiResult<SocialLoginResponse> =
        ApiResult.Success(SocialLoginResponse.SignUpRequired)
    var postSignUpLoginResult: ApiResult<SocialLoginResponse>? = null
    var loginCount = 0
    var loginProvider: SocialLoginProvider? = null
    var loginIdToken: String? = null
    var createUploadProvider: SocialLoginProvider? = null
    var createUploadIdToken: String? = null
    var createUploadCount = 0
    var signUpCount = 0
    val signupTokens = mutableListOf<String>()
    val signUpResults = ArrayDeque<ApiResult<SocialSignUpResponse>>()

    override suspend fun socialLogin(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SocialLoginResponse> {
        loginProvider = provider
        loginIdToken = idToken
        loginCount += 1
        return if (loginCount > 1) {
            postSignUpLoginResult ?: loginResult
        } else {
            loginResult
        }
    }

    override suspend fun createSignatureUpload(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SignatureUploadResponse> {
        createUploadProvider = provider
        createUploadIdToken = idToken
        createUploadCount += 1
        return ApiResult.Success(
            SignatureUploadResponse(
                uploadId = "upload-id",
                uploadUrl = "https://example.com/upload",
                expiresInSeconds = 300,
                signupToken = "signup-token",
                signupTokenExpiresInSeconds = 1_800,
            ),
        )
    }

    override suspend fun socialSignUp(signupToken: String): ApiResult<SocialSignUpResponse> {
        signUpCount += 1
        signupTokens += signupToken
        return signUpResults.removeFirst()
    }
}

private class FakeSignatureUploader : SignatureUploader {
    var uploadCount = 0
    var uploadedPng = ByteArray(0)
    var result: SignatureUploadResult = SignatureUploadResult.Success

    override suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult {
        uploadCount += 1
        uploadedPng = signaturePng
        return result
    }
}

private class FakeSessionStore : SessionStore {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)
    private val mutableSession = MutableStateFlow<LocalSession>(LocalSession.SignedOut)

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
