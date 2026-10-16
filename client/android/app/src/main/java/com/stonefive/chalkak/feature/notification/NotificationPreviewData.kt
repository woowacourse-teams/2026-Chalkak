package com.stonefive.chalkak.feature.notification

import com.stonefive.chalkak.R

val previewNotifications = listOf(
    NotificationItemUiState(
        id = "preview-today-topic",
        title = "9월 22일 오늘의 주제를 확인해보세요.",
        timeText = "18:00",
        isUnread = true,
    ),
    NotificationItemUiState(
        id = "preview-yesterday-topic",
        title = "9월 21일 오늘의 주제를 확인해보세요.",
        timeText = "어제 21:10",
        isUnread = true,
        thumbnailModel = R.drawable.home_feed_photo,
    ),
)
