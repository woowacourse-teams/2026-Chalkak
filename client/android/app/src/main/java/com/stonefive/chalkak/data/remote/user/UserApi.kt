package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse
import retrofit2.Response
import retrofit2.http.GET

interface UserApi {
    @GET("users/me/signature")
    suspend fun getMySignature(): Response<UserSignatureResponse>
}
