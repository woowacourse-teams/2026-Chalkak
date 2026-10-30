package com.stonefive.chalkak.data.remote.appversion

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface AppVersionApi {
    @Headers("Cache-Control: no-cache, no-store", "Pragma: no-cache")
    @GET("/api/v1/app-version")
    suspend fun getAppVersion(@Query("platform") platform: String = "ANDROID"): AppVersionResponse
}

@Serializable
data class AppVersionResponse(
    val platform: String,
    val minimumSupportedVersion: String? = null,
    val storeUrl: String? = null,
)
