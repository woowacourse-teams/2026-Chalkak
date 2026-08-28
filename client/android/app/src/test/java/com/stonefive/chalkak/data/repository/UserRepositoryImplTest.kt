package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploadResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploader
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import com.stonefive.chalkak.domain.model.SignatureUpdateFailure
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserRepositoryImplTest {
    private val userDataSource = FakeUserDataSource()
    private val signatureUploader = FakeUserSignatureUploader()
    private val repository = UserRepositoryImpl(userDataSource, signatureUploader)

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
}

private class FakeUserDataSource : UserDataSource {
    var mySignatureResult: ApiResult<UserSignatureResponse> = ApiResult.Success(
        UserSignatureResponse(
            originalImageUrl = "original-signature-url",
            thumbnailImageUrl = "thumbnail-signature-url",
        ),
    )

    var updatedUploadId: String? = null

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
