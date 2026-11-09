package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `설정을 불러오면 로딩을 끝내고 값을 제공한다`() = runTest {
        val repository = FakeNotificationSettingsRepository()
        assertTrue(NotificationSettingsUiState().isLoading)

        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(repository.settings, viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `설정 조회 실패는 로딩을 끝내고 Toast 메시지를 보낸다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            settingsResult = NotificationResult.Failure(NotificationFailure.Network)
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.pushSettings)
        assertEquals(
            "푸시 알림 설정을 불러오지 못했어요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `좋아요 알림 토글은 다른 설정과 독립적으로 저장한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            settings = NotificationPushSettings(true, false, true, true)
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateLikePushEnabled(false)
        advanceUntilIdle()

        assertEquals(
            NotificationPushSettingsUpdate(likePushEnabled = false),
            repository.updates.single(),
        )
        assertEquals(NotificationPushSettings(true, false, true, false), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `좋아요 알림 저장 실패는 좋아요 값만 되돌리고 중복 입력을 막는다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateLikePushEnabled(false)
        viewModel.updateLikePushEnabled(true)
        viewModel.updateTopicPushEnabled(false)
        assertTrue(viewModel.uiState.value.isLikePushSaving)
        assertEquals(2, repository.updates.size)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isLikePushSaving)
        assertTrue(viewModel.uiState.value.pendingMessage is UiMessage.Toast)
    }

    @Test
    fun `개별 토글은 선택한 알림만 변경한다`() = runTest {
        val repository = FakeNotificationSettingsRepository()
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        advanceUntilIdle()
        assertEquals(NotificationPushSettingsUpdate(topicPushEnabled = false), repository.updates.last())
        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)

        viewModel.updateModerationPushEnabled(false)
        advanceUntilIdle()
        assertEquals(NotificationPushSettingsUpdate(moderationPushEnabled = false), repository.updates.last())
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)

        viewModel.updateTopicPushEnabled(true)
        advanceUntilIdle()
        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)

        viewModel.updateModerationPushEnabled(true)
        advanceUntilIdle()
        assertEquals(NotificationPushSettings(true, true), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `AI 피드백 알림 토글은 다른 설정과 독립적으로 저장한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            settings = NotificationPushSettings(true, false, true)
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateAiReviewPushEnabled(false)
        advanceUntilIdle()

        assertEquals(
            NotificationPushSettingsUpdate(aiReviewPushEnabled = false),
            repository.updates.last(),
        )
        assertEquals(NotificationPushSettings(true, false, false), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `AI 알림 저장 실패는 동시에 저장한 주제 설정을 유지한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply { suspendUpdates = true }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateAiReviewPushEnabled(false)
        viewModel.updateAiReviewPushEnabled(true)
        viewModel.updateTopicPushEnabled(false)
        assertEquals(2, repository.updates.size)
        assertTrue(viewModel.uiState.value.isAiReviewPushSaving)
        assertTrue(viewModel.uiState.value.isTopicPushSaving)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        advanceUntilIdle()
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(false, true, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isAiReviewPushSaving)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertTrue(viewModel.uiState.value.pendingMessage != null)
    }

    @Test
    fun `개별 설정 저장 실패 시 실패한 설정만 복원한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            settings = NotificationPushSettings(true, false)
            updateResult = NotificationResult.Failure(NotificationFailure.Http(500, null))
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateModerationPushEnabled(true)
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
        assertTrue(viewModel.uiState.value.pendingMessage != null)
    }

    @Test
    fun `다른 설정은 저장 중이어도 각각 요청한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)

        assertEquals(
            listOf(
                NotificationPushSettingsUpdate(topicPushEnabled = false),
                NotificationPushSettingsUpdate(moderationPushEnabled = false),
            ),
            repository.updates,
        )
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)
        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertTrue(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(0, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `같은 설정 저장 중 반복 입력은 추가 요청하지 않는다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateTopicPushEnabled(true)

        assertEquals(listOf(NotificationPushSettingsUpdate(topicPushEnabled = false)), repository.updates)
        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)
        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)

        repository.completeOnlyPendingUpdate(NotificationResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
    }

    @Test
    fun `한 설정 저장 실패는 진행 중인 다른 설정 값을 되돌리지 않는다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertTrue(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }

    @Test
    fun `나중 요청이 실패해도 먼저 성공한 다른 설정 값은 유지한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Success(Unit))
        advanceUntilIdle()
        repository.completePendingUpdate(1, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }

    @Test
    fun `두 설정 저장이 모두 실패하면 각 설정을 기존 값으로 복원한다`() = runTest {
        val repository = FakeNotificationSettingsRepository().apply {
            suspendUpdates = true
        }
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()
        repository.completePendingUpdate(1, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }
}

private class FakeNotificationSettingsRepository : NotificationRepository {
    var settings = NotificationPushSettings(true, true)
    var settingsResult: NotificationResult<NotificationPushSettings>? = null
    var updateResult: NotificationResult<Unit> = NotificationResult.Success(Unit)
    var suspendUpdates: Boolean = false
    val updates = mutableListOf<NotificationPushSettingsUpdate>()
    val pendingUpdates = mutableListOf<PendingPushSettingsUpdate>()

    override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> =
        settingsResult ?: NotificationResult.Success(settings)

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> {
        updates += settings
        if (suspendUpdates) {
            val pending = PendingPushSettingsUpdate()
            pendingUpdates += pending
            return pending.result.await()
        }
        return updateResult
    }

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage> = error("Not used")

    override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
        error("Not used")

    override suspend fun getUnreadStatus(): NotificationResult<Boolean> = error("Not used")

    override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = error("Not used")

    override suspend fun markAllAsRead(): NotificationResult<Unit> = error("Not used")

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> = error("Not used")
}

private data class PendingPushSettingsUpdate(
    val result: CompletableDeferred<NotificationResult<Unit>> = CompletableDeferred(),
)

private fun FakeNotificationSettingsRepository.completePendingUpdate(
    index: Int,
    result: NotificationResult<Unit>,
) {
    pendingUpdates[index].result.complete(result)
}

private fun FakeNotificationSettingsRepository.completeOnlyPendingUpdate(result: NotificationResult<Unit>) {
    val pendingUpdate = pendingUpdates.single()
    pendingUpdate.result.complete(result)
}
