package com.stonefive.chalkak.feature.reminder

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
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
}

private class FakeReminderPreferenceRepository : ReminderPreferenceRepository {
    private val mutablePreference = MutableStateFlow<ReminderPreference>(
        ReminderPreference.Unconfigured,
    )
    override val preference: StateFlow<ReminderPreference> = mutablePreference

    override suspend fun enable(
        hour: Int,
        minute: Int,
    ) {
        mutablePreference.value = ReminderPreference.Enabled(hour, minute)
    }

    override suspend fun disable() {
        mutablePreference.value = ReminderPreference.Disabled
    }
}
