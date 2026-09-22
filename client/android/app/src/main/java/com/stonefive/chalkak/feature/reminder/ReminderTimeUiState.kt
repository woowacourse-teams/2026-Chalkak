package com.stonefive.chalkak.feature.reminder

import com.stonefive.chalkak.core.ui.UiMessage

data class ReminderTimeUiState(
    val selectedOption: ReminderTimeOption = ReminderTimeOption.EVENING,
    val customHour: Int? = null,
    val customMinute: Int? = null,
    val saveStatus: ReminderSaveStatus = ReminderSaveStatus.IDLE,
    val pendingMessage: UiMessage? = null,
) {
    val customTimeLabel: String?
        get() = customHour?.let { hour ->
            customMinute?.let { minute -> "%02d:%02d".format(hour, minute) }
        }
}

enum class ReminderTimeOption(
    val label: String,
    val description: String?,
    val hour: Int?,
    val minute: Int?,
) {
    MORNING(label = "아침 8:00", description = "출근길에", hour = 8, minute = 0),
    NOON(label = "점심 12:00", description = "잠깐 쉴 때", hour = 12, minute = 0),
    EVENING(label = "저녁 18:00", description = "해 질 무렵", hour = 18, minute = 0),
    CUSTOM(label = "직접 설정하기", description = null, hour = null, minute = null),
}

enum class ReminderSaveStatus {
    IDLE,
    SAVING,
    SAVED,
}
