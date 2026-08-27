package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider

interface AuthDataSource {
    suspend fun socialLogin(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SocialLoginResponse>

    suspend fun createSignatureUpload(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SignatureUploadResponse>

    suspend fun socialSignUp(signupToken: String): ApiResult<SocialSignUpResponse>
}
