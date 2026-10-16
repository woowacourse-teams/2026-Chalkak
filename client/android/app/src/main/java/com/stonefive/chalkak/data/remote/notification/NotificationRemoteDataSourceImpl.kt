package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.notification.model.NotificationDetailResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPageResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsRequest
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsResponse
import com.stonefive.chalkak.data.remote.notification.model.PushDeviceRegistrationRequest
import com.stonefive.chalkak.data.remote.notification.model.UnreadStatusResponse

class NotificationRemoteDataSourceImpl(
    private val api: NotificationApi,
    private val requestExecutor: ApiRequestExecutor,
) : NotificationRemoteDataSource {
    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): ApiResult<NotificationPageResponse> = requestExecutor.execute {
        api.getNotifications(page, pageSize)
    }

    override suspend fun getNotification(notificationId: String): ApiResult<NotificationDetailResponse> =
        requestExecutor.execute {
            api.getNotification(notificationId)
        }

    override suspend fun getUnreadStatus(): ApiResult<UnreadStatusResponse> = requestExecutor.execute {
        api.getUnreadStatus()
    }

    override suspend fun markAsRead(notificationId: String): ApiResult<Unit> = requestExecutor.executeNoContent {
        api.markAsRead(notificationId)
    }

    override suspend fun markAllAsRead(): ApiResult<Unit> = requestExecutor.executeNoContent {
        api.markAllAsRead()
    }

    override suspend fun getPushSettings(): ApiResult<NotificationPushSettingsResponse> = requestExecutor.execute {
        api.getPushSettings()
    }

    override suspend fun updatePushSettings(request: NotificationPushSettingsRequest): ApiResult<Unit> =
        requestExecutor.executeNoContent {
            api.updatePushSettings(request)
        }

    override suspend fun registerCurrentPushDevice(fcmToken: String): ApiResult<Unit> =
        requestExecutor.executeNoContent {
            api.registerCurrentPushDevice(PushDeviceRegistrationRequest(fcmToken))
        }
}
