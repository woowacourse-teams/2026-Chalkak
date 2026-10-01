package com.stonefive.chalkak.feature.notification

data class NotificationUiState(val notifications: List<NotificationItemUiState> = emptyList())

data class NotificationItemUiState(
    val id: String,
    val title: String,
    val timeText: String,
    val isUnread: Boolean,
    val thumbnailModel: Any? = null,
)
