package com.stonefive.chalkak.data.remote.display.model

data class DisplayResponse(
    val selectedDate: String,
    val latestDate: String,
    val earliestDate: String,
    val topic: String,
    val photos: List<DisplayPhotoResponse>,
    val featuredPhotos: List<DisplayPhotoResponse>,
)

data class DisplayPhotoResponse(
    val id: String,
    val imageUrl: String,
    val signatureUrl: String,
    val contentDescription: String,
    val title: String?,
    val likeCount: Int,
)
