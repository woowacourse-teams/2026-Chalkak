package com.stonefive.chalkak.feature.notification

import com.stonefive.chalkak.core.ui.UiMessage

data class NotificationUiState(
    val notifications: List<NotificationItemUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val hasUnread: Boolean = false,
    val errorMessage: String? = null,
    val pendingMessage: UiMessage? = null,
)

data class NotificationItemUiState(
    val id: String,
    val title: String,
    val timeText: String,
    val body: String = "",
    val readAt: String? = null,
    val isUnread: Boolean = readAt == null,
    val type: String = "",
    val sourceType: String? = null,
    val sourceId: String? = null,
    val thumbnailModel: Any? = null,
)
