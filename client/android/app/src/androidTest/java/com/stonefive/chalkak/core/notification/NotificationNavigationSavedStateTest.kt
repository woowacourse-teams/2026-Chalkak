package com.stonefive.chalkak.core.notification

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationSavedStateTest {
    private val push = NotificationPushData("event", "notification", "APPROVED", "POST", "post")

    @Test
    fun pendingPushSurvivesProcessRecreation() {
        val original = PendingNotificationNavigationStore()
        original.enqueue(push)
        val savedState = Bundle()
        original.saveTo(savedState)

        val restored = PendingNotificationNavigationStore()
        restored.restoreFrom(savedState)

        assertEquals(push, restored.pending.value)
    }

    @Test
    fun consumedPushIsNotSavedForProcessRecreation() {
        val original = PendingNotificationNavigationStore()
        original.enqueue(push)
        original.consume(push)
        val savedState = Bundle()
        original.saveTo(savedState)

        val restored = PendingNotificationNavigationStore()
        restored.restoreFrom(savedState)

        assertNull(restored.pending.value)
    }

    @Test
    fun recreationDoesNotRestorePushConsumedAfterStateWasSaved() {
        val store = PendingNotificationNavigationStore()
        store.enqueue(push)
        val savedState = Bundle()
        store.saveTo(savedState)
        store.consume(push)

        store.restoreFrom(savedState)

        assertNull(store.pending.value)
    }

    @Test
    fun recreationPreservesNewPushReceivedAfterStateWasSaved() {
        val store = PendingNotificationNavigationStore()
        store.enqueue(push)
        val savedState = Bundle()
        store.saveTo(savedState)
        val newerPush = push.copy(eventId = "new-event", notificationId = "new-notification")
        store.enqueue(newerPush)

        store.restoreFrom(savedState)

        assertEquals(newerPush, store.pending.value)
    }

    @Test
    fun freshClickCanEnqueuePreviouslyConsumedPush() {
        val store = PendingNotificationNavigationStore()
        store.enqueue(push)
        store.consume(push)

        store.enqueue(push)

        assertEquals(push, store.pending.value)
    }

    @Test
    fun restorationWithoutPendingPushDoesNotCreateNavigation() {
        val store = PendingNotificationNavigationStore()

        store.restoreFrom(Bundle())

        assertNull(store.pending.value)
    }
}
