package com.stonefive.chalkak.data.local.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stonefive.chalkak.MainActivity
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.notification.EVENT_ID_KEY
import com.stonefive.chalkak.core.notification.EXTRA_IS_NOTIFICATION_PUSH
import com.stonefive.chalkak.core.notification.NOTIFICATION_ID_KEY
import com.stonefive.chalkak.core.notification.NOTIFICATION_TYPE_KEY
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.core.notification.SOURCE_ID_KEY
import com.stonefive.chalkak.core.notification.SOURCE_TYPE_KEY

object PushNotificationManager {
    const val CHANNEL_ID = "moderation_push"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "게시물 검수 알림",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "게시물 승인, 반려 결과를 알려드려요."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        payload: NotificationPushData,
        title: String,
        body: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(context)
        val stableKey = payload.notificationId ?: payload.eventId ?: "notification"
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("chalkak://notification/$stableKey")
            putExtra(EXTRA_IS_NOTIFICATION_PUSH, true)
            payload.eventId?.let { putExtra(EVENT_ID_KEY, it) }
            payload.notificationId?.let { putExtra(NOTIFICATION_ID_KEY, it) }
            payload.type?.let { putExtra(NOTIFICATION_TYPE_KEY, it) }
            payload.sourceType?.let { putExtra(SOURCE_TYPE_KEY, it) }
            payload.sourceId?.let { putExtra(SOURCE_ID_KEY, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            stableKey.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round))
            .setColor(ContextCompat.getColor(context, R.color.chalkak_notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(stableKey.hashCode(), notification)
    }
}
