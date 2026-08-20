package com.stonefive.chalkak.feature.settings

sealed interface SettingsUiEvent {
    data object NavigateToLogin : SettingsUiEvent
}
