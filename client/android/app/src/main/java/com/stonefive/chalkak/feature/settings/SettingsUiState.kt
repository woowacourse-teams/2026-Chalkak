package com.stonefive.chalkak.feature.settings

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isAccountActionInProgress: Boolean = false,
    val signatureUrl: String? = null,
    val signatureErrorMessage: String? = null,
    val versionName: String,
    val accountDialog: SettingsAccountDialog? = null,
)

enum class SettingsAccountDialog {
    LOGOUT,
    WITHDRAW,
}
