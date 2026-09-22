package com.stonefive.chalkak.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReminderTimeViewModel(private val repository: ReminderPreferenceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderTimeUiState())
    val uiState: StateFlow<ReminderTimeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preference.collect { preference ->
                if (preference is ReminderPreference.Enabled) {
                    _uiState.update { state -> state.withSavedTime(preference.hour, preference.minute) }
                }
            }
        }
    }

    fun selectOption(option: ReminderTimeOption) {
        _uiState.update { state -> state.copy(selectedOption = option) }
    }

    fun selectCustomTime(
        hour: Int,
        minute: Int,
    ) {
        _uiState.update { state ->
            state.copy(
                selectedOption = ReminderTimeOption.CUSTOM,
                customHour = hour,
                customMinute = minute,
            )
        }
    }

    fun saveSelection() {
        if (_uiState.value.saveStatus == ReminderSaveStatus.SAVING) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(saveStatus = ReminderSaveStatus.SAVING) }
            val state = _uiState.value
            val hour = state.selectedOption.hour ?: state.customHour ?: DEFAULT_HOUR
            val minute = state.selectedOption.minute ?: state.customMinute ?: DEFAULT_MINUTE
            repository.enable(hour, minute)
            _uiState.update { current -> current.copy(saveStatus = ReminderSaveStatus.SAVED) }
        }
    }

    fun disable() {
        if (_uiState.value.saveStatus == ReminderSaveStatus.SAVING) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(saveStatus = ReminderSaveStatus.SAVING) }
            repository.disable()
            _uiState.update { state -> state.copy(saveStatus = ReminderSaveStatus.SAVED) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                ReminderTimeViewModel(application.appContainer.reminderPreferenceRepository)
            }
        }
    }
}

private fun ReminderTimeUiState.withSavedTime(
    hour: Int,
    minute: Int,
): ReminderTimeUiState {
    val preset = ReminderTimeOption.entries.firstOrNull { option ->
        option.hour == hour && option.minute == minute
    }
    return if (preset != null) {
        copy(selectedOption = preset)
    } else {
        copy(
            selectedOption = ReminderTimeOption.CUSTOM,
            customHour = hour,
            customMinute = minute,
        )
    }
}

private const val DEFAULT_HOUR = 18
private const val DEFAULT_MINUTE = 0
