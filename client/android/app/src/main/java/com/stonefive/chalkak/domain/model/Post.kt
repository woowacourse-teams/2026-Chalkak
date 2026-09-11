package com.stonefive.chalkak.domain.model

import java.time.Instant

data class Post(
    val id: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String,
    val signatureOriginalImageUrl: String,
    val signatureThumbnailImageUrl: String? = null,
    val contentDescription: String,
    val submittedAt: Instant? = null,
    val title: String?,
    val likeCount: Int,
    val isLiked: Boolean = false,
    val isOwnedByCurrentUser: Boolean = false,
)
