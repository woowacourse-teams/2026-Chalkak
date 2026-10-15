package com.stonefive.chalkak.core.notification

import android.os.Bundle

fun PendingNotificationNavigationStore.saveTo(outState: Bundle) {
    val pushState = pending.value?.let { push ->
        Bundle().apply {
            putString(EVENT_ID_KEY, push.eventId)
            putString(NOTIFICATION_ID_KEY, push.notificationId)
            putString(NOTIFICATION_TYPE_KEY, push.type)
            putString(SOURCE_TYPE_KEY, push.sourceType)
            putString(SOURCE_ID_KEY, push.sourceId)
        }
    }
    outState.putBundle(PENDING_PUSH_STATE_KEY, pushState)
}

fun PendingNotificationNavigationStore.restoreFrom(savedState: Bundle) {
    val push = savedState.getBundle(PENDING_PUSH_STATE_KEY)?.let { state ->
        NotificationPushData(
            eventId = state.getString(EVENT_ID_KEY),
            notificationId = state.getString(NOTIFICATION_ID_KEY),
            type = state.getString(NOTIFICATION_TYPE_KEY),
            sourceType = state.getString(SOURCE_TYPE_KEY),
            sourceId = state.getString(SOURCE_ID_KEY),
        )
    }
    restore(push)
}

private const val PENDING_PUSH_STATE_KEY = "pending_notification_navigation"
