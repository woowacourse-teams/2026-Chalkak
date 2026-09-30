package com.stonefive.chalkak.feature.notification

import androidx.lifecycle.ViewModel
import com.stonefive.chalkak.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        NotificationUiState(notifications = sampleNotifications),
    )
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()
}

private val sampleNotifications = listOf(
    NotificationItemUiState(
        id = "today-topic-2026-09-22",
        title = "9월 22일 오늘의 주제를 확인해보세요.",
        timeText = "18:00",
        isUnread = true,
    ),
    NotificationItemUiState(
        id = "today-topic-2026-09-21",
        title = "9월 21일 오늘의 주제를 확인해보세요.",
        timeText = "어제 21:10",
        isUnread = true,
        thumbnailModel = R.drawable.home_feed_photo,
    ),
)
