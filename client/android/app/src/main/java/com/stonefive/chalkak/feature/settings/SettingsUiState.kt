package com.stonefive.chalkak.feature.settings

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isAccountActionInProgress: Boolean = false,
    val signatureModel: Any? = null,
    val versionName: String,
)
