package com.stonefive.chalkak.data.local.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Retained for users upgrading directly from any version that scheduled local reminders. */
object LegacyReminderCleanup {
    fun cancel(context: Context) {
        val applicationContext = context.applicationContext
        // Component and request code must match the old scheduler; extras do not identify an intent.
        val operation = PendingIntent.getBroadcast(
            applicationContext,
            LEGACY_ALARM_REQUEST_CODE,
            Intent(applicationContext, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (operation != null) {
            applicationContext.getSystemService(AlarmManager::class.java).cancel(operation)
            operation.cancel()
        }
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.activeNotifications
            .filter { notification ->
                notification.id == LEGACY_NOTIFICATION_ID &&
                    notification.tag == null &&
                    notification.notification.channelId == LEGACY_CHANNEL_ID
            }.forEach { notification ->
                notificationManager.cancel(notification.id)
            }
    }
}

private const val LEGACY_ALARM_REQUEST_CODE = 1_800
private const val LEGACY_NOTIFICATION_ID = 1_801
private const val LEGACY_CHANNEL_ID = "daily_topic_reminder"
