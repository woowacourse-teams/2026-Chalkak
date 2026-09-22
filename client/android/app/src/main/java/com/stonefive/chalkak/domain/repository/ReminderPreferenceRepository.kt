package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.ReminderPreference
import kotlinx.coroutines.flow.StateFlow

interface ReminderPreferenceRepository {
    val preference: StateFlow<ReminderPreference>

    suspend fun enable(
        hour: Int,
        minute: Int,
    )

    suspend fun disable()
}
