package com.stonefive.chalkak.feature.notification

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class NotificationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `삭제된 알림 클릭은 토스트를 표시하고 목록과 미읽음을 갱신한다`() = runTest {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationViewModel(repository)

        assertFalse(viewModel.markAsRead("deleted"))

        val message = viewModel.uiState.value.pendingMessage as UiMessage.Toast
        assertEquals("삭제된 게시물입니다", message.text)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, repository.listRequestCount)
        assertEquals(2, repository.unreadRequestCount)
        viewModel.onMessageShown(message.id)
        assertNull(viewModel.uiState.value.pendingMessage)
    }

    @Test
    fun `이전 토스트 완료는 새 토스트를 지우지 않는다`() = runTest {
        val viewModel = NotificationViewModel(FakeNotificationRepository())
        viewModel.markAsRead("first")
        val firstMessage = viewModel.uiState.value.pendingMessage!!
        viewModel.markAsRead("second")
        val secondMessage = viewModel.uiState.value.pendingMessage!!

        viewModel.onMessageShown(firstMessage.id)

        assertEquals(secondMessage, viewModel.uiState.value.pendingMessage)
    }

    @Test
    fun `네트워크 실패는 삭제 토스트로 표시하지 않는다`() = runTest {
        val repository = FakeNotificationRepository().apply {
            readResult = NotificationResult.Failure(NotificationFailure.Network)
        }
        val viewModel = NotificationViewModel(repository)

        assertFalse(viewModel.markAsRead("notification"))

        assertNull(viewModel.uiState.value.pendingMessage)
        assertEquals("네트워크 연결을 확인해 주세요.", viewModel.uiState.value.errorMessage)
    }

    private class FakeNotificationRepository : NotificationRepository {
        var readResult: NotificationResult<Unit> = NotificationResult.Failure(NotificationFailure.Http(404, null))
        var listRequestCount = 0
        var unreadRequestCount = 0

        override suspend fun getNotifications(
            page: Int,
            pageSize: Int,
        ): NotificationResult<NotificationPage> {
            listRequestCount++
            return NotificationResult.Success(NotificationPage(page, pageSize, false, emptyList()))
        }

        override suspend fun getUnreadStatus(): NotificationResult<Boolean> {
            unreadRequestCount++
            return NotificationResult.Success(false)
        }

        override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = readResult

        override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
            error("Unexpected detail request")

        override suspend fun markAllAsRead(): NotificationResult<Unit> = error("Unexpected mark-all request")

        override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> =
            error("Unexpected settings request")

        override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> =
            error("Unexpected settings update")

        override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> =
            error("Unexpected device registration")
    }
}
