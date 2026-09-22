package com.stonefive.chalkak.feature.reminder

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderTimeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `선택한 프리셋 시간을 저장한다`() = runTest {
        val repository = FakeReminderPreferenceRepository()
        val viewModel = ReminderTimeViewModel(repository)

        viewModel.selectOption(ReminderTimeOption.MORNING)
        viewModel.saveSelection()
        advanceUntilIdle()

        assertEquals(ReminderPreference.Enabled(8, 0), repository.preference.value)
        assertEquals(ReminderSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun `직접 설정한 시간을 저장한다`() = runTest {
        val repository = FakeReminderPreferenceRepository()
        val viewModel = ReminderTimeViewModel(repository)

        viewModel.selectCustomTime(hour = 21, minute = 35)
        viewModel.saveSelection()
        advanceUntilIdle()

        assertEquals(ReminderPreference.Enabled(21, 35), repository.preference.value)
    }

    @Test
    fun `알림을 건너뛰면 비활성 상태를 저장한다`() = runTest {
        val repository = FakeReminderPreferenceRepository()
        val viewModel = ReminderTimeViewModel(repository)

        viewModel.disable()
        advanceUntilIdle()

        assertEquals(ReminderPreference.Disabled, repository.preference.value)
        assertEquals(ReminderSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun `알림 설정 저장에 실패하면 다시 시도할 수 있다`() = runTest {
        val repository = FakeReminderPreferenceRepository().apply {
            enableResult = Result.failure(IOException("write failed"))
        }
        val viewModel = ReminderTimeViewModel(repository)

        viewModel.saveSelection()
        advanceUntilIdle()

        assertEquals(ReminderSaveStatus.IDLE, viewModel.uiState.value.saveStatus)
        assertEquals(
            "알림 설정을 저장하지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `알림 비활성화에 실패하면 다시 시도할 수 있다`() = runTest {
        val repository = FakeReminderPreferenceRepository().apply {
            disableResult = Result.failure(IOException("write failed"))
        }
        val viewModel = ReminderTimeViewModel(repository)

        viewModel.disable()
        advanceUntilIdle()

        assertEquals(ReminderSaveStatus.IDLE, viewModel.uiState.value.saveStatus)
        assertEquals(
            "알림 설정을 저장하지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }
}

private class FakeReminderPreferenceRepository : ReminderPreferenceRepository {
    private val mutablePreference = MutableStateFlow<ReminderPreference>(
        ReminderPreference.Unconfigured,
    )
    override val preference: StateFlow<ReminderPreference> = mutablePreference
    var enableResult: Result<Unit> = Result.success(Unit)
    var disableResult: Result<Unit> = Result.success(Unit)

    override suspend fun enable(
        hour: Int,
        minute: Int,
    ): Result<Unit> {
        if (enableResult.isFailure) return enableResult
        mutablePreference.value = ReminderPreference.Enabled(hour, minute)
        return enableResult
    }

    override suspend fun disable(): Result<Unit> {
        if (disableResult.isFailure) return disableResult
        mutablePreference.value = ReminderPreference.Disabled
        return disableResult
    }
}
