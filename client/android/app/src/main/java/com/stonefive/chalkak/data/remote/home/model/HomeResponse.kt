package com.stonefive.chalkak.data.remote.home.model

import androidx.annotation.DrawableRes

data class HomeResponse(
    val dateLabel: String,
    val topic: String,
    val photos: List<HomePhotoResponse>,
    val likedPhotoIds: Set<String>,
)

data class HomePhotoResponse(
    val id: String,
    val image: HomeImageResponse,
    val signatureImage: HomeImageResponse?,
    val contentDescription: String,
    val story: String?,
    val likeCount: Int,
)

sealed interface HomeImageResponse {
    data class Local(@param:DrawableRes val resourceId: Int) : HomeImageResponse

    data class Remote(val url: String) : HomeImageResponse
}

data class HomeLikeResponse(val likeCount: Int)
