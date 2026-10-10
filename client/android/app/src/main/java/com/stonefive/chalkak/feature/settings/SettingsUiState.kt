package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationPushSettings

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isAccountActionInProgress: Boolean = false,
    val signatureUrl: String? = null,
    val pushSettings: NotificationPushSettings? = null,
    val isPushSettingsLoading: Boolean = false,
    val isPushSettingsSaving: Boolean = false,
    val versionName: String,
    val accountDialog: SettingsAccountDialog? = null,
    val pendingMessage: UiMessage? = null,
)

enum class SettingsAccountDialog {
    LOGOUT,
    WITHDRAW,
}
