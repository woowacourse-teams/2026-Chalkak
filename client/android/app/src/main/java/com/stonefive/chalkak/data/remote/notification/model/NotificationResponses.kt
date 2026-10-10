package com.stonefive.chalkak.data.remote.notification.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationPageResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val notifications: List<NotificationItemResponse>,
)

@Serializable
data class NotificationItemResponse(
    val id: String,
    val type: String,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val title: String,
    val body: String,
    val thumbnailImageUrl: String? = null,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class NotificationDetailResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val originalImageUrl: String? = null,
    val rejectionReason: String? = null,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class UnreadStatusResponse(val hasUnread: Boolean)

@Serializable
data class NotificationPushSettingsResponse(
    val topicPushEnabled: Boolean,
    val moderationPushEnabled: Boolean,
)

@Serializable
data class NotificationPushSettingsRequest(
    val topicPushEnabled: Boolean? = null,
    val moderationPushEnabled: Boolean? = null,
)

@Serializable
data class PushDeviceRegistrationRequest(val fcmToken: String)
