package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.UserSessionState
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
data object NotificationSettings

@Serializable
data class NotificationDetail(val notificationId: String)

@Serializable
data class Display(
    val date: String,
    val sort: String = "RANDOM",
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
    // 저장된 이전 버전 경로에는 authorId가 없을 수 있어 빈 값으로 복원한다(도메인 Post는 필수).
    val authorId: String = "",
    val sourceAuthorId: String? = null,
)

@Serializable
data class AuthorPosts(
    val authorId: String,
    val signatureThumbnailImageUrl: String?,
    val isMine: Boolean,
)

/**
 * 지금 로그인한 사용자 기준으로 이 작가 화면이 내 화면인지 판단한다.
 * 로그인 후 복원한 경로에는 이전 계정 기준의 [AuthorPosts.isMine]이 남아 있을 수 있으므로,
 * 로그인 상태면 회원 ID와 [AuthorPosts.authorId]를 비교해 다시 계산하고, 세션을 아직 모르면 경로 값을 쓴다.
 *
 * 예: A 계정의 내 작가 화면(isMine=true)을 B 계정 로그인 후 복원하면 false가 된다.
 */
fun AuthorPosts.isMineFor(session: UserSessionState): Boolean =
    (session as? UserSessionState.Authenticated)?.let { it.userId == authorId } ?: isMine

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

/** 문의 작성 화면. */
@Serializable
data object Feedback

/** 고객센터(내 문의 목록). */
@Serializable
data object FeedbackList

/** 문의 상세. 목록이나 답변 알림(알림함·푸시)에서 연다. */
@Serializable
data class FeedbackDetail(val feedbackId: String)

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
