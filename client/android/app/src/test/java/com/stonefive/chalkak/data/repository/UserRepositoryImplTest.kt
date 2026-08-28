package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserRepositoryImplTest {
    private val userDataSource = FakeUserDataSource()
    private val repository = UserRepositoryImpl(userDataSource)

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
}

private class FakeUserDataSource : UserDataSource {
    var mySignatureResult: ApiResult<UserSignatureResponse> = ApiResult.Success(
        UserSignatureResponse(
            originalImageUrl = "original-signature-url",
            thumbnailImageUrl = "thumbnail-signature-url",
        ),
    )

    override suspend fun getMySignature(): ApiResult<UserSignatureResponse> = mySignatureResult
}
