package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult

interface NotificationRepository {
    suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage>

    suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail>

    suspend fun getUnreadStatus(): NotificationResult<Boolean>

    suspend fun markAsRead(notificationId: String): NotificationResult<Unit>

    suspend fun markAllAsRead(): NotificationResult<Unit>

    suspend fun getPushSettings(): NotificationResult<NotificationPushSettings>

    suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit>

    suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit>
}
