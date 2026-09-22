package com.stonefive.chalkak.domain.model

sealed interface ReminderPreference {
    data object Loading : ReminderPreference

    data object Unconfigured : ReminderPreference

    data object Disabled : ReminderPreference

    data class Enabled(
        val hour: Int,
        val minute: Int,
    ) : ReminderPreference
}
