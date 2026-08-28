package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class PostContent(
    val topicDate: LocalDate,
    val dateLabel: String,
    val topic: String,
    val photos: List<Post>,
    val likedPhotoIds: Set<String>,
    val currentPage: Int = HomeQuery.FIRST_PAGE,
    val hasNext: Boolean = false,
    val randomSeed: String? = null,
)
