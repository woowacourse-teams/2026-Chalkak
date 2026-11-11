package com.stonefive.chalkak.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingNotificationNavigationStoreTest {
    @Test
    fun `새로 시작한 화면에 푸시가 없으면 남아 있던 푸시를 버린다`() {
        // Given  앞서 연 화면이 처리하지 못하고 끝난 푸시(프로세스는 살아 있음)
        val store = PendingNotificationNavigationStore()
        store.enqueue(NotificationPushData("event", null, null, null, null, screen = "chalkak://screen/record"))

        // When   런처로 새로 시작해 받은 푸시가 없다
        store.startWith(null)

        // Then   남은 푸시가 없다(로그인 뒤 갑자기 다른 화면이 열리지 않는다)
        assertNull(store.pending.value)
    }

    @Test
    fun `새로 시작한 화면에 푸시가 있으면 그 푸시를 기다린다`() {
        // Given  남아 있던 푸시
        val store = PendingNotificationNavigationStore()
        store.enqueue(NotificationPushData("old", null, null, null, null))
        val push = NotificationPushData("new", null, null, null, null, screen = "chalkak://screen/settings")

        // When   알림을 눌러 새로 시작한다
        store.startWith(push)

        // Then   새 푸시
        assertEquals(push, store.pending.value)
    }

    @Test
    fun `새로 시작한 뒤에는 저장해 둔 이전 상태로 되돌리지 않는다`() {
        // Given  새로 시작하며 남은 푸시를 버렸다
        val store = PendingNotificationNavigationStore()
        store.startWith(null)

        // When   이전 상태 복원이 늦게 불린다
        store.restore(NotificationPushData("saved", null, null, null, null))

        // Then   복원하지 않는다
        assertNull(store.pending.value)
    }
}
