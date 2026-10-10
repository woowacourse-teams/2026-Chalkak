package com.stonefive.chalkak.feature.notification

import com.stonefive.chalkak.domain.model.NotificationDetail

data class NotificationDetailUiState(
    val detail: NotificationDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
