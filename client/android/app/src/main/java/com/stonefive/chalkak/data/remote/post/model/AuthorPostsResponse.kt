package com.stonefive.chalkak.data.remote.post.model

import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import kotlinx.serialization.Serializable

@Serializable
data class AuthorPostsResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val posts: List<AuthorPostResponse>,
)

@Serializable
data class AuthorPostResponse(
    val id: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String? = null,
    val topic: TopicResponse,
)
