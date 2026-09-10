package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class TodayPostResponse(
    val topicDate: String,
    val isPosted: Boolean,
    val postId: String? = null,
    val moderationStatus: String? = null,
)
