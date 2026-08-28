package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploadResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploader
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import com.stonefive.chalkak.domain.model.AccountWithdrawalException
import com.stonefive.chalkak.domain.model.SignatureUpdateFailure
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserRepositoryImplTest {
    private val userDataSource = FakeUserDataSource()
    private val signatureUploader = FakeUserSignatureUploader()
    private val sessionStore = FakeUserSessionStore()
    private val repository = UserRepositoryImpl(userDataSource, signatureUploader, sessionStore)

    @Test
    fun `사인 응답을 사용자 프로필로 변환한다`() = runTest {
        val profile = repository.getMySignature()

        assertEquals(
            UserProfile(
                signatureUrl = "original-signature-url",
                signatureThumbnailUrl = "thumbnail-signature-url",
            ),
            profile,
        )
    }

    @Test
    fun `서명 조회의 401 응답을 인증 오류로 변환한다`() = runTest {
        userDataSource.mySignatureResult = ApiResult.Failure(ApiError.Http(401, null))

        val error = runCatching { repository.getMySignature() }.exceptionOrNull()

        assertEquals(
            UserProfileLoadFailure.UNAUTHORIZED,
            (error as UserProfileLoadException).reason,
        )
    }

    @Test
    fun `사인 변경은 업로드 URL로 PNG를 업로드한 뒤 변경을 확정한다`() = runTest {
        val signaturePng = byteArrayOf(1, 2, 3)

        val result = repository.updateMySignature(signaturePng)

        assertEquals(
            SignatureUpdateResult.Success(
                UserProfile(
                    signatureUrl = "updated-signature-url",
                    signatureThumbnailUrl = "updated-signature-url",
                ),
            ),
            result,
        )
        assertEquals(signaturePng.toList(), signatureUploader.uploadedPng?.toList())
        assertEquals("upload-id", userDataSource.updatedUploadId)
    }

    @Test
    fun `사인 업로드가 실패하면 변경 확정을 호출하지 않는다`() = runTest {
        signatureUploader.result = SignatureUploadResult.NetworkFailure

        val result = repository.updateMySignature(byteArrayOf(1))

        assertEquals(
            SignatureUpdateResult.Failure(SignatureUpdateFailure.NETWORK_UNAVAILABLE),
            result,
        )
        assertEquals(null, userDataSource.updatedUploadId)
    }

    @Test
    fun `회원탈퇴 API 성공 후 세션을 삭제한다`() = runTest {
        sessionStore.setAuthenticated()

        repository.withdraw()

        assertEquals(1, userDataSource.deleteAccountCount)
        assertEquals(UserSessionState.SignedOut, sessionStore.sessionState.value)
    }

    @Test
    fun `회원탈퇴 API 실패 시 세션을 삭제하지 않는다`() = runTest {
        sessionStore.setAuthenticated()
        userDataSource.deleteAccountResult = ApiResult.Failure(ApiError.Network)

        val error = runCatching { repository.withdraw() }.exceptionOrNull()

        assertEquals(AccountWithdrawalException::class, error!!::class)
        assertEquals(UserSessionState.Authenticated("user-id"), sessionStore.sessionState.value)
    }
}

private class FakeUserDataSource : UserDataSource {
    var mySignatureResult: ApiResult<UserSignatureResponse> = ApiResult.Success(
        UserSignatureResponse(
            originalImageUrl = "original-signature-url",
            thumbnailImageUrl = "thumbnail-signature-url",
        ),
    )

    var updatedUploadId: String? = null
    var deleteAccountCount = 0
    var deleteAccountResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun getMySignature(): ApiResult<UserSignatureResponse> = mySignatureResult

    override suspend fun createSignatureUpload(): ApiResult<SignatureUploadResponse> = ApiResult.Success(
        SignatureUploadResponse(
            uploadId = "upload-id",
            uploadUrl = "https://example.com/upload",
            expiresInSeconds = 300,
        ),
    )

    override suspend fun updateSignature(signatureOriginalUploadId: String): ApiResult<SignatureUpdateResponse> {
        updatedUploadId = signatureOriginalUploadId
        return ApiResult.Success(SignatureUpdateResponse("updated-signature-url"))
    }

    override suspend fun deleteMyAccount(): ApiResult<Unit> {
        deleteAccountCount += 1
        return deleteAccountResult
    }
}

private class FakeUserSignatureUploader : SignatureUploader {
    var result: SignatureUploadResult = SignatureUploadResult.Success
    var uploadedPng: ByteArray? = null

    override suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult {
        uploadedPng = signaturePng
        return result
    }
}

private class FakeUserSessionStore : SessionStore {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)

    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    fun setAuthenticated() {
        mutableSessionState.value = UserSessionState.Authenticated("user-id")
    }

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
