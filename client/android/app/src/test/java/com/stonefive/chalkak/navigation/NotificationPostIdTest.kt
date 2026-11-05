package com.stonefive.chalkak.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPostIdTest {
    @Test
    fun `UUID 형식의 게시물 ID만 받는다`() {
        assertTrue(isNotificationPostId("019a0010-0000-7000-8000-000000000003"))
        assertTrue(isNotificationPostId("019A0010-0000-7000-8000-00000000000B"))
        listOf("", "post-id", "1-1-1-1-1", "019a0010-0000-7000-8000-000000000003/../likes").forEach {
            assertFalse(it, isNotificationPostId(it))
        }
    }
}
