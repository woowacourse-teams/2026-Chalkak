package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.model.request.LogoutRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SignatureUploadRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialLoginRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialSignUpRequest
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponseDto
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider

class AuthDataSourceImpl(
    private val api: AuthApi,
    private val requestExecutor: ApiRequestExecutor,
) : AuthDataSource {
    override suspend fun socialLogin(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SocialLoginResponse> = when (
        val result = requestExecutor.execute {
            api.socialLogin(
                SocialLoginRequest(
                    provider = provider.name,
                    idToken = idToken,
                ),
            )
        }
    ) {
        is ApiResult.Success ->
            result.value
                .toSocialLoginResponse()
                ?.let(ApiResult<SocialLoginResponse>::Success)
                ?: ApiResult.Failure(ApiError.InvalidResponse)

        is ApiResult.Failure -> result
    }

    override suspend fun createSignatureUpload(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SignatureUploadResponse> = requestExecutor.execute {
        api.createSignatureUpload(
            SignatureUploadRequest(
                provider = provider.name,
                idToken = idToken,
            ),
        )
    }

    override suspend fun socialSignUp(signupToken: String): ApiResult<SocialSignUpResponse> = requestExecutor.execute {
        api.socialSignUp(
            SocialSignUpRequest(signupToken = signupToken),
        )
    }

    override suspend fun logout(refreshToken: String): ApiResult<Unit> = requestExecutor.executeNoContent {
        api.logout(LogoutRequest(refreshToken = refreshToken))
    }

    private fun SocialLoginResponseDto.toSocialLoginResponse(): SocialLoginResponse? = when (status) {
        LOGIN_SUCCESS -> {
            val validUserId = userId?.takeIf(String::isNotBlank)
            val validAccessToken = accessToken?.takeIf(String::isNotBlank)
            val validExpiresIn = expiresIn?.takeIf { it > 0 }
            val validRefreshToken = refreshToken?.takeIf(String::isNotBlank)
            val validRefreshTokenExpiresIn = refreshTokenExpiresIn?.takeIf { it > 0 }
            if (
                validUserId != null &&
                validAccessToken != null &&
                validExpiresIn != null &&
                validRefreshToken != null &&
                validRefreshTokenExpiresIn != null
            ) {
                SocialLoginResponse.LoginSuccess(
                    userId = validUserId,
                    accessToken = validAccessToken,
                    expiresIn = validExpiresIn,
                    refreshToken = validRefreshToken,
                    refreshTokenExpiresIn = validRefreshTokenExpiresIn,
                )
            } else {
                null
            }
        }

        SIGN_UP_REQUIRED -> SocialLoginResponse.SignUpRequired

        else -> null
    }

    private companion object {
        const val LOGIN_SUCCESS = "LOGIN_SUCCESS"
        const val SIGN_UP_REQUIRED = "SIGN_UP_REQUIRED"
    }
}
