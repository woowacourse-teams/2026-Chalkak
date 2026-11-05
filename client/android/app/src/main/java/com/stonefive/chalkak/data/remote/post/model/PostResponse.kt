package com.stonefive.chalkak.data.remote.post.model

import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import kotlinx.serialization.Serializable

@Serializable
data class PostDetailResponse(
    val id: String,
    val authorId: String,
    val topic: TopicResponse,
    val originalImageUrl: String,
    val thumbnailImageUrl: String,
    val signatureOriginalImageUrl: String,
    val title: String? = null,
    val description: String? = null,
    val likeCount: Long,
    val isLiked: Boolean,
    val isMine: Boolean = false,
    // 조회자가 작성자 본인일 때만 내려온다. 다른 사람이면 null.
    val instagramConsent: InstagramConsentResponse? = null,
)

@Serializable
data class InstagramConsentResponse(
    val agreed: Boolean,
    val instagramId: String? = null,
)

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
    val authorId: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String,
    val signatureOriginalImageUrl: String,
    val signatureThumbnailImageUrl: String,
    val title: String? = null,
    val submittedAt: String? = null,
    val likeCount: Long,
    val isLiked: Boolean,
    val isMine: Boolean = false,
)

@Serializable
data class PostLikeResponse(
    val postId: String,
    val likeCount: Long,
    val isLiked: Boolean,
)
