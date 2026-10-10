package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsRequest
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationItem
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository

class NotificationRepositoryImpl(private val remoteDataSource: NotificationRemoteDataSource) : NotificationRepository {
    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage> = remoteDataSource
        .getNotifications(page, pageSize)
        .mapSuccess { response ->
            NotificationPage(
                currentPage = response.currentPage,
                pageSize = response.pageSize,
                hasNext = response.hasNext,
                notifications = response.notifications.map { item ->
                    NotificationItem(
                        id = item.id,
                        type = item.type,
                        sourceType = item.sourceType,
                        sourceId = item.sourceId,
                        title = item.title,
                        body = item.body,
                        thumbnailImageUrl = item.thumbnailImageUrl,
                        readAt = item.readAt,
                        createdAt = item.createdAt,
                    )
                },
            )
        }

    override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
        remoteDataSource
            .getNotification(notificationId)
            .mapSuccess { response ->
                NotificationDetail(
                    id = response.id,
                    type = response.type,
                    title = response.title,
                    body = response.body,
                    originalImageUrl = response.originalImageUrl,
                    rejectionReason = response.rejectionReason,
                    readAt = response.readAt,
                    createdAt = response.createdAt,
                )
            }

    override suspend fun getUnreadStatus(): NotificationResult<Boolean> = remoteDataSource
        .getUnreadStatus()
        .mapSuccess { it.hasUnread }

    override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = remoteDataSource
        .markAsRead(notificationId)
        .mapSuccess { Unit }

    override suspend fun markAllAsRead(): NotificationResult<Unit> = remoteDataSource
        .markAllAsRead()
        .mapSuccess { Unit }

    override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> = remoteDataSource
        .getPushSettings()
        .mapSuccess { response ->
            NotificationPushSettings(
                topicPushEnabled = response.topicPushEnabled,
                moderationPushEnabled = response.moderationPushEnabled,
            )
        }

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> =
        remoteDataSource
            .updatePushSettings(
                NotificationPushSettingsRequest(
                    topicPushEnabled = settings.topicPushEnabled,
                    moderationPushEnabled = settings.moderationPushEnabled,
                ),
            ).mapSuccess { Unit }

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> = remoteDataSource
        .registerCurrentPushDevice(fcmToken)
        .mapSuccess { Unit }
}

private fun <T, R> ApiResult<T>.mapSuccess(transform: (T) -> R): NotificationResult<R> = when (this) {
    is ApiResult.Success -> NotificationResult.Success(transform(value))
    is ApiResult.Failure -> NotificationResult.Failure(error.toNotificationFailure())
}

private fun ApiError.toNotificationFailure(): NotificationFailure = when (this) {
    ApiError.Network -> NotificationFailure.Network
    ApiError.InvalidResponse -> NotificationFailure.InvalidResponse
    is ApiError.Http -> NotificationFailure.Http(statusCode, errorCode)
}
