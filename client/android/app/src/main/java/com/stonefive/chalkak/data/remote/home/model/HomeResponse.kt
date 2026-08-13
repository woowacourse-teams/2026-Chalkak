package com.stonefive.chalkak.data.remote.home.model

data class HomeResponse(
    val dateLabel: String,
    val topic: String,
    val photos: List<HomePhotoResponse>,
    val likedPhotoIds: Set<String>,
)

data class HomePhotoResponse(
    val id: String,
    val imageUrl: String,
    val signatureUrl: String?,
    val contentDescription: String,
    val story: String?,
    val likeCount: Int,
)

data class HomeLikeResponse(val likeCount: Int)
