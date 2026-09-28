package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostCalendarResponse(
    val year: Int,
    val month: Int,
    val posts: List<PostCalendarItemResponse>,
)

@Serializable
data class PostCalendarMonthsResponse(val months: List<PostCalendarMonthResponse>)

@Serializable
data class PostCalendarMonthResponse(
    val year: Int,
    val month: Int,
)

@Serializable
data class PostCalendarItemResponse(
    val topicDate: String,
    val postId: String,
    val thumbnailImageUrl: String,
    val status: String,
)
