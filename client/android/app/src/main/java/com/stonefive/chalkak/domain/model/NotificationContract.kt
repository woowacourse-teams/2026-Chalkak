package com.stonefive.chalkak.domain.model

data class NotificationPage(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val notifications: List<NotificationItem>,
)

data class NotificationItem(
    val id: String,
    val type: String,
    val sourceType: String?,
    val sourceId: String?,
    val title: String,
    val body: String,
    val thumbnailImageUrl: String?,
    val readAt: String?,
    val createdAt: String,
)

data class NotificationDetail(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val originalImageUrl: String?,
    val rejectionReason: String?,
    val readAt: String?,
    val createdAt: String,
)

data class NotificationPushSettings(
    val topicPushEnabled: Boolean,
    val moderationPushEnabled: Boolean,
)

data class NotificationPushSettingsUpdate(
    val topicPushEnabled: Boolean? = null,
    val moderationPushEnabled: Boolean? = null,
)

sealed interface NotificationResult<out T> {
    data class Success<T>(val value: T) : NotificationResult<T>

    data class Failure(val reason: NotificationFailure) : NotificationResult<Nothing>
}

sealed interface NotificationFailure {
    data object Network : NotificationFailure

    data object InvalidResponse : NotificationFailure

    data class Http(
        val statusCode: Int,
        val errorCode: String?,
    ) : NotificationFailure
}
