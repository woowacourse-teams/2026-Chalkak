package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateRequest
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface UserApi {
    @GET("users/me/signature")
    suspend fun getMySignature(): Response<UserSignatureResponse>

    @POST("users/me/signature/uploads")
    suspend fun createSignatureUpload(): Response<SignatureUploadResponse>

    @PUT("users/me/signature")
    suspend fun updateSignature(@Body request: SignatureUpdateRequest): Response<SignatureUpdateResponse>

    @DELETE("users/me")
    suspend fun deleteMyAccount(): Response<Unit>
}
