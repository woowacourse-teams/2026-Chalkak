package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationPushSettings

data class NotificationSettingsUiState(
    val pushSettings: NotificationPushSettings? = null,
    val isLoading: Boolean = true,
    val isTopicPushSaving: Boolean = false,
    val isModerationPushSaving: Boolean = false,
    val isAiReviewPushSaving: Boolean = false,
    val isLikePushSaving: Boolean = false,
    val pendingMessage: UiMessage? = null,
)
