package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.request.RefreshTokenRequest
import com.stonefive.chalkak.data.remote.auth.model.response.RefreshTokenResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RefreshApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): Response<RefreshTokenResponseDto>
}
