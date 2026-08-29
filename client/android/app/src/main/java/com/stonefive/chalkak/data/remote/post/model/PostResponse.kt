package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostPageResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val randomSeed: String? = null,
    val posts: List<PostResponse>,
)

@Serializable
data class PostResponse(
    val id: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String,
    val signatureOriginalImageUrl: String,
    val signatureThumbnailImageUrl: String,
    val title: String? = null,
    val likeCount: Long,
    val isLiked: Boolean,
)

@Serializable
data class PostLikeResponse(
    val postId: String,
    val likeCount: Long,
    val isLiked: Boolean,
)
