package com.stonefive.chalkak.domain.model

data class PostContent(
    val dateLabel: String,
    val topic: String,
    val photos: List<Post>,
    val likedPhotoIds: Set<String>,
)
