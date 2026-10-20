package com.stonefive.chalkak.data.remote.banner

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BannerApi {
    @GET("banners")
    suspend fun getBanners(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<BannerPageResponse>
}

@Serializable
data class BannerPageResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val banners: List<BannerResponse>,
)

@Serializable
data class BannerResponse(
    val id: String,
    val title: String,
    val subtitle: String,
    val webViewUrl: String,
    val thumbnailUrl: String,
    val createdAt: String,
)
