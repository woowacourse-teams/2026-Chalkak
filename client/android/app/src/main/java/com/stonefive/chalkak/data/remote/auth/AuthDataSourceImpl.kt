package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.model.request.SignatureUploadRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialLoginRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialSignUpRequest
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider

class AuthDataSourceImpl(
    private val api: AuthApi,
    private val requestExecutor: ApiRequestExecutor,
) : AuthDataSource {
    override suspend fun socialLogin(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SocialLoginResponse> = requestExecutor.execute {
        api.socialLogin(
            SocialLoginRequest(
                provider = provider.name,
                idToken = idToken,
            ),
        )
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
}
