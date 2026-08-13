package com.stonefive.chalkak.domain.model

data class HomeContent(
    val dateLabel: String,
    val topic: String,
    val photos: List<HomePhoto>,
    val likedPhotoIds: Set<String>,
)
