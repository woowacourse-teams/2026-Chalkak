package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.notification.model.NotificationDetailResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPageResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsRequest
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsResponse
import com.stonefive.chalkak.data.remote.notification.model.UnreadStatusResponse

interface NotificationRemoteDataSource {
    suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): ApiResult<NotificationPageResponse>

    suspend fun getNotification(notificationId: String): ApiResult<NotificationDetailResponse>

    suspend fun getUnreadStatus(): ApiResult<UnreadStatusResponse>

    suspend fun markAsRead(notificationId: String): ApiResult<Unit>

    suspend fun markAllAsRead(): ApiResult<Unit>

    suspend fun getPushSettings(): ApiResult<NotificationPushSettingsResponse>

    suspend fun updatePushSettings(request: NotificationPushSettingsRequest): ApiResult<Unit>

    suspend fun registerCurrentPushDevice(fcmToken: String): ApiResult<Unit>
}
