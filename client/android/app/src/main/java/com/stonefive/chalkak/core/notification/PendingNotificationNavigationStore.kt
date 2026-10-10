package com.stonefive.chalkak.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PendingNotificationNavigationStore {
    private val mutablePending = MutableStateFlow<NotificationPushData?>(null)
    val pending: StateFlow<NotificationPushData?> = mutablePending.asStateFlow()

    fun enqueue(data: NotificationPushData) {
        mutablePending.value = data
    }

    fun consume(data: NotificationPushData) {
        mutablePending.update { current ->
            if (current == data) null else current
        }
    }
}
