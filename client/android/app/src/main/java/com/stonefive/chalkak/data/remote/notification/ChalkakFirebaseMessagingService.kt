package com.stonefive.chalkak.data.remote.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.data.local.notification.PushNotificationManager

class ChalkakFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? ChalkakApplication)?.appContainer?.registerPushDevice(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = NotificationPushData.from(message.data)
            ?: NotificationPushData(null, null, null, null, null)
        PushNotificationManager.show(
            context = this,
            payload = data,
            title = message.notification?.title ?: getString(com.stonefive.chalkak.R.string.app_name),
            body = message.notification?.body ?: "새 알림이 있어요. 알림함에서 확인해 주세요.",
        )
    }
}
