package com.stonefive.chalkak.domain.model

data class PhotoContent(
    val dateLabel: String,
    val topic: String,
    val photos: List<Photo>,
    val likedPhotoIds: Set<String>,
)
