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

    // 화면을 새로 시작할 때 부른다. 프로세스가 살아 있으면 앞서 끝난 화면이 처리하지 못한 푸시가 남아 있을 수 있다.
    fun startWith(data: NotificationPushData?) {
        hasCurrentState = true
        mutablePending.value = data
    }

    fun discard() {
        startWith(null)
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
