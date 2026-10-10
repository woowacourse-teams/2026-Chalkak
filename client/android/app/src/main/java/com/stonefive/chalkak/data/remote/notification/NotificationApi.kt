package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.remote.notification.model.NotificationDetailResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPageResponse
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsRequest
import com.stonefive.chalkak.data.remote.notification.model.NotificationPushSettingsResponse
import com.stonefive.chalkak.data.remote.notification.model.PushDeviceRegistrationRequest
import com.stonefive.chalkak.data.remote.notification.model.UnreadStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<NotificationPageResponse>

    @GET("notifications/{notificationId}")
    suspend fun getNotification(@Path("notificationId") notificationId: String): Response<NotificationDetailResponse>

    @GET("notifications/unread-status")
    suspend fun getUnreadStatus(): Response<UnreadStatusResponse>

    @PATCH("notifications/{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String): Response<Unit>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(): Response<Unit>

    @GET("notification-settings")
    suspend fun getPushSettings(): Response<NotificationPushSettingsResponse>

    @PATCH("notification-settings")
    suspend fun updatePushSettings(@Body request: NotificationPushSettingsRequest): Response<Unit>

    @PUT("push-devices/current")
    suspend fun registerCurrentPushDevice(@Body request: PushDeviceRegistrationRequest): Response<Unit>
}
