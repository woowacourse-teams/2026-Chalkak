package com.stonefive.chalkak.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PendingNotificationNavigationStore {
    private var hasCurrentState = false
    private val mutablePending = MutableStateFlow<NotificationPushData?>(null)
    val pending: StateFlow<NotificationPushData?> = mutablePending.asStateFlow()

    fun enqueue(data: NotificationPushData) {
        hasCurrentState = true
        mutablePending.value = data
    }

    // A surviving process may have consumed or replaced the saved push since state was saved.
    fun restore(data: NotificationPushData?) {
        if (hasCurrentState) return
        hasCurrentState = true
        mutablePending.value = data
    }

    fun consume(data: NotificationPushData) {
        hasCurrentState = true
        mutablePending.update { current ->
            if (current == data) null else current
        }
    }
}
