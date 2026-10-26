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
data object Home

@Serializable
data class WebPage(
    val title: String,
    val url: String,
)

@Serializable
data object Notifications

@Serializable
data class NotificationDetail(val notificationId: String)

@Serializable
data class Display(
    val date: String,
    val sort: String = "LATEST",
)

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
    val topicDate: String? = null,
    val isOwnedByCurrentUser: Boolean = false,
    val fetchDetail: Boolean = true,
    val authorId: String = "",
    val sourceAuthorId: String? = null,
)

@Serializable
data class AuthorPosts(
    val authorId: String,
    val signatureThumbnailImageUrl: String?,
    val isMine: Boolean,
)

@Serializable
data class FeedById(
    val postId: String,
    val isOwnedByCurrentUser: Boolean,
    val thumbnailImageUrl: String? = null,
    val openAiReviewResult: Boolean = false,
)

@Serializable
data object Record

@Serializable
data object Settings

@Serializable
data object Feedback

@Serializable
data class PhotoUpload(val topicDate: String)

@Serializable
data class PhotoUploadSuccess(
    val imageModel: String,
    val caption: String,
    val date: String,
    val topic: String,
    val moderationStatus: String,
    val quarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.5f,
    val cropWidth: Float = 1f,
    val cropHeight: Float = 1f,
    val filter: String = "Original",
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val blackPoint: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
)
