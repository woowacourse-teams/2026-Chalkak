package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.request.SignatureUploadRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialLoginRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialSignUpRequest
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponseDto
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/social-login")
    suspend fun socialLogin(@Body request: SocialLoginRequest): Response<SocialLoginResponseDto>

    @POST("auth/social-signup/signature/uploads")
    suspend fun createSignatureUpload(@Body request: SignatureUploadRequest): Response<SignatureUploadResponse>

    @POST("auth/social-signup")
    suspend fun socialSignUp(@Body request: SocialSignUpRequest): Response<SocialSignUpResponse>
}
