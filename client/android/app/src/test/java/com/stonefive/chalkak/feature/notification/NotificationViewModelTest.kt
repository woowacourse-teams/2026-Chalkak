package com.stonefive.chalkak.feature.notification

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationItem
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `삭제된 알림 클릭은 토스트를 표시하고 목록과 미읽음을 갱신한다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readResult = NotificationResult.Failure(NotificationFailure.Http(404, null))
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()
        val initialListRequestCount = repository.listRequestCount
        val initialUnreadRequestCount = repository.unreadRequestCount

        assertFalse(viewModel.markAsRead("deleted"))

        val message = viewModel.uiState.value.pendingMessage as UiMessage.Toast
        assertEquals("삭제된 게시물입니다", message.text)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(initialListRequestCount + 1, repository.listRequestCount)
        assertEquals(initialUnreadRequestCount + 1, repository.unreadRequestCount)
        viewModel.onMessageShown(message.id)
        assertNull(viewModel.uiState.value.pendingMessage)
    }

    @Test
    fun `이전 토스트 완료는 새 토스트를 지우지 않는다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readResult = NotificationResult.Failure(NotificationFailure.Http(404, null))
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()
        viewModel.markAsRead("first")
        val firstMessage = viewModel.uiState.value.pendingMessage!!
        viewModel.markAsRead("second")
        val secondMessage = viewModel.uiState.value.pendingMessage!!

        viewModel.onMessageShown(firstMessage.id)

        assertEquals(secondMessage, viewModel.uiState.value.pendingMessage)
    }

    @Test
    fun `네트워크 실패는 읽음 상태와 목록을 유지하고 재시도 토스트를 표시한다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readResult = NotificationResult.Failure(NotificationFailure.Network)
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()
        val initialListRequestCount = repository.listRequestCount
        val initialUnreadRequestCount = repository.unreadRequestCount

        assertTrue(viewModel.markAsRead("notification"))

        val item = viewModel.uiState.value.notifications
            .single()
        assertTrue(item.isUnread)
        assertNull(item.readAt)
        assertEquals(
            "읽음 상태를 저장하지 못했어요. 네트워크 연결을 확인해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(initialListRequestCount, repository.listRequestCount)
        assertEquals(initialUnreadRequestCount, repository.unreadRequestCount)
    }

    @Test
    fun `네트워크 예외도 네트워크 실패 토스트를 표시하고 목록을 갱신하지 않는다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readException = IllegalStateException("connection failed")
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()
        val initialListRequestCount = repository.listRequestCount

        assertTrue(viewModel.markAsRead("notification"))

        assertEquals(
            "읽음 상태를 저장하지 못했어요. 네트워크 연결을 확인해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertEquals(initialListRequestCount, repository.listRequestCount)
        assertTrue(
            viewModel.uiState.value.notifications
                .single()
                .isUnread,
        )
    }

    @Test
    fun `취소는 그대로 전파되고 토스트와 목록 갱신을 수행하지 않는다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readException = CancellationException("cancelled")
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()
        val initialListRequestCount = repository.listRequestCount

        var wasCancelled = false
        try {
            viewModel.markAsRead("notification")
        } catch (_: CancellationException) {
            wasCancelled = true
        }

        assertTrue(wasCancelled)
        assertNull(viewModel.uiState.value.pendingMessage)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(initialListRequestCount, repository.listRequestCount)
        assertTrue(
            viewModel.uiState.value.notifications
                .single()
                .isUnread,
        )
    }

    @Test
    fun `인증 오류는 읽음 처리하지 않고 로그인 오류를 표시한다`() = runTest {
        listOf(401, 403).forEach { statusCode ->
            val repository = FakeNotificationRepository().apply {
                readResult = NotificationResult.Failure(NotificationFailure.Http(statusCode, null))
            }
            val viewModel = NotificationViewModel(repository)
            advanceUntilIdle()
            val initialListRequestCount = repository.listRequestCount

            assertFalse(viewModel.markAsRead("notification"))

            assertEquals("로그인이 필요해요.", viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.pendingMessage)
            assertEquals(initialListRequestCount + 1, repository.listRequestCount)
            assertTrue(
                viewModel.uiState.value.notifications
                    .single()
                    .isUnread,
            )
        }
    }

    @Test
    fun `네트워크 이외의 실패는 상세 이동을 허용하지 않는다`() = runTest {
        listOf(NotificationFailure.Http(500, null), NotificationFailure.InvalidResponse).forEach { failure ->
            val repository = FakeNotificationRepository().apply {
                readResult = NotificationResult.Failure(failure)
            }
            val viewModel = NotificationViewModel(repository)
            advanceUntilIdle()

            assertFalse(viewModel.markAsRead("notification"))
            assertNotNull(viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.pendingMessage)
            assertNull(
                viewModel.uiState.value.notifications
                    .single()
                    .readAt,
            )
        }
    }

    @Test
    fun `읽음 처리 성공 시 해당 알림만 읽음으로 변경한다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readResult = NotificationResult.Success(Unit)
        }
        val viewModel = NotificationViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.markAsRead("notification"))

        val item = viewModel.uiState.value.notifications
            .single()
        assertFalse(item.isUnread)
        assertNotNull(item.readAt)
        assertEquals(1, repository.listRequestCount)
    }

    @Test
    fun `AI 결과 알림은 서버 데이터와 알림 ID를 유지한다`() = runTest {
        listOf("AI_REVIEW_COMPLETED", "AI_REVIEW_FAILED").forEach { type ->
            val repository = FakeNotificationRepository().apply {
                items = listOf(
                    NotificationItem(
                        id = "ai-notification",
                        type = type,
                        sourceType = "POST",
                        sourceId = "reviewed-post",
                        title = "서버가 정한 제목",
                        body = "서버가 정한 본문",
                        thumbnailImageUrl = "https://example.com/photo.jpg",
                        readAt = null,
                        createdAt = "2026-10-22T00:00:00Z",
                    ),
                )
            }
            val viewModel = NotificationViewModel(repository)
            advanceUntilIdle()

            val item = viewModel.uiState.value.notifications
                .single()
            assertEquals(type, item.type)
            assertEquals("POST", item.sourceType)
            assertEquals("reviewed-post", item.sourceId)
            assertEquals("서버가 정한 제목", item.title)
            assertEquals("서버가 정한 본문", item.body)
            assertEquals("https://example.com/photo.jpg", item.thumbnailModel)
            assertTrue(item.isUnread)
            assertTrue(repository.readIds.isEmpty())

            assertTrue(viewModel.markAsRead(item.id))

            assertEquals(listOf("ai-notification"), repository.readIds)
            assertFalse(
                viewModel.uiState.value.notifications
                    .single()
                    .isUnread,
            )
        }
    }

    private class FakeNotificationRepository : NotificationRepository {
        var items = listOf(notification)
        val readIds = mutableListOf<String>()
        var readResult: NotificationResult<Unit> = NotificationResult.Success(Unit)
        var readException: Exception? = null
        var listRequestCount = 0
        var unreadRequestCount = 0

        override suspend fun getNotifications(
            page: Int,
            pageSize: Int,
        ): NotificationResult<NotificationPage> {
            listRequestCount++
            return NotificationResult.Success(
                NotificationPage(page, pageSize, false, items),
            )
        }

        override suspend fun getUnreadStatus(): NotificationResult<Boolean> {
            unreadRequestCount++
            return NotificationResult.Success(true)
        }

        override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> {
            readIds += notificationId
            readException?.let { throw it }
            return readResult
        }

        override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
            error("Unexpected detail request")

        override suspend fun markAllAsRead(): NotificationResult<Unit> = error("Unexpected mark-all request")

        override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> =
            error("Unexpected settings request")

        override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> =
            error("Unexpected settings update")

        override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> =
            error("Unexpected device registration")

        private companion object {
            val notification = NotificationItem(
                id = "notification",
                type = "MODERATION",
                sourceType = "POST",
                sourceId = "post",
                title = "알림",
                body = "본문",
                thumbnailImageUrl = null,
                readAt = null,
                createdAt = "2026-10-14T00:00:00Z",
            )
        }
    }
}
