package com.stonefive.chalkak.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import kotlinx.coroutines.flow.StateFlow

class ReminderGateViewModel(repository: ReminderPreferenceRepository) : ViewModel() {
    val preference: StateFlow<ReminderPreference> = repository.preference

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                ReminderGateViewModel(application.appContainer.reminderPreferenceRepository)
            }
        }
    }
}
