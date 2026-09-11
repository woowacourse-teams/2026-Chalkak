package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostImageUploadResponse(
    val uploadId: String,
    val uploadUrl: String,
    val expiresInSeconds: Long,
    val contentType: String,
    val maxBytes: Long,
)

@Serializable
data class PostCreateRequest(
    val topicId: String,
    val photoUploadId: String,
    val title: String? = null,
)

@Serializable
data class PostCreateResponse(
    val postId: String,
    val moderationStatus: String,
)
