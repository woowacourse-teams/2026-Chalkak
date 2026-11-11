package com.stonefive.chalkak.core.notification

/** 앱이 스스로 만든 알림을 기기에 띄운다. */
interface LocalNotificationPublisher {
    /** 알림을 띄우고 true를 돌려준다. 앱 알림이나 알림 채널이 꺼져 있으면 띄우지 않고 false를 돌려준다. */
    fun publish(
        payload: NotificationPushData,
        title: String,
        body: String,
    ): Boolean
}
