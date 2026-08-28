package com.stonefive.chalkak.data.remote.home.model

import kotlinx.serialization.Serializable

@Serializable
data class HomeTopicResponse(
    val id: String,
    val title: String,
    val topicDate: String,
)

@Serializable
data class HomePostPageResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val randomSeed: String? = null,
    val posts: List<HomePostResponse>,
)

@Serializable
data class HomePostResponse(
    val id: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String? = null,
    val signatureOriginalImageUrl: String,
    val signatureThumbnailImageUrl: String,
    val title: String? = null,
    val likeCount: Long,
    val isLiked: Boolean,
)

@Serializable
data class HomeLikeResponse(
    val postId: String,
    val likeCount: Long,
    val isLiked: Boolean,
)
