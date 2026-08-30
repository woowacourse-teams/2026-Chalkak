package com.stonefive.chalkak.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Login

@Serializable
data object Terms

@Serializable
data object OnboardingSignature

@Serializable
data object ChangeSignature

@Serializable
data object OnboardingSignaturePreview

@Serializable
data object ChangeSignaturePreview

@Serializable
data object Today

@Serializable
data class Display(val date: String)

@Serializable
data class Feed(
    val postId: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String,
    val signatureOriginalImageUrl: String,
    val signatureThumbnailImageUrl: String? = null,
    val contentDescription: String,
    val title: String?,
    val likeCount: Int,
    val isLiked: Boolean = false,
    val dateLabel: String,
    val topic: String,
    val isOwnedByCurrentUser: Boolean = false,
    val fetchDetail: Boolean = true,
)

@Serializable
data object Record

@Serializable
data object Settings

@Serializable
data class PhotoUpload(val topicDate: String)

@Serializable
data class PhotoUploadSuccess(
    val imageModel: String,
    val caption: String,
    val dateLabel: String,
    val topic: String,
    val moderationStatus: String,
)
