package com.stonefive.chalkak.feature.developertools

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.core.notification.SCREEN_PUSH_URI_PREFIX
import com.stonefive.chalkak.data.local.notification.PushNotificationManager

/** 개발자 도구에서 보낼 수 있는 화면 푸시. [INVALID]는 허용하지 않는 URI로 알림함 + 안내를 확인한다. */
enum class DeveloperToolsTestNotification(
    @param:StringRes val labelRes: Int,
    val uri: String,
) {
    NOTIFICATIONS(R.string.developer_tools_test_notification_notifications, "${SCREEN_PUSH_URI_PREFIX}notifications"),
    NOTIFICATION_SETTINGS(
        R.string.developer_tools_test_notification_notification_settings,
        "${SCREEN_PUSH_URI_PREFIX}notification-settings",
    ),
    RECORD(R.string.developer_tools_test_notification_record, "${SCREEN_PUSH_URI_PREFIX}record"),
    SETTINGS(R.string.developer_tools_test_notification_settings, "${SCREEN_PUSH_URI_PREFIX}settings"),
    INVALID(R.string.developer_tools_test_notification_invalid, "${SCREEN_PUSH_URI_PREFIX}unknown"),
}

/** [notification]을 로컬 알림으로 띄운다. 알림이 꺼져 있으면 띄우지 않고 안내한다. */
internal fun Context.sendTestNotification(notification: DeveloperToolsTestNotification) {
    val notificationManager = NotificationManagerCompat.from(this)
    val channel = notificationManager.getNotificationChannel(PushNotificationManager.CHANNEL_ID)
    val channelBlocked = channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE
    if (!notificationManager.areNotificationsEnabled() || channelBlocked) {
        Toast.makeText(this, R.string.developer_tools_test_notification_disabled, Toast.LENGTH_SHORT).show()
        return
    }
    val label = getString(notification.labelRes)
    PushNotificationManager.show(
        context = this,
        payload = NotificationPushData(
            eventId = "developer-tools-${System.currentTimeMillis()}",
            notificationId = null,
            type = null,
            sourceType = null,
            sourceId = null,
            screen = notification.uri,
        ),
        title = getString(R.string.developer_tools_test_notification_title, label),
        body = notification.uri,
    )
}
