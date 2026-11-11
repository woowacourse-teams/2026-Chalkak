package com.stonefive.chalkak.data.local.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.stonefive.chalkak.core.notification.LocalNotificationPublisher
import com.stonefive.chalkak.core.notification.NotificationPushData

class LocalNotificationPublisherImpl(private val context: Context) : LocalNotificationPublisher {
    override fun publish(
        payload: NotificationPushData,
        title: String,
        body: String,
    ): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        val channel = notificationManager.getNotificationChannel(PushNotificationManager.CHANNEL_ID)
        val channelBlocked = channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        if (!notificationManager.areNotificationsEnabled() || channelBlocked) return false
        PushNotificationManager.show(context, payload, title, body)
        return true
    }
}
