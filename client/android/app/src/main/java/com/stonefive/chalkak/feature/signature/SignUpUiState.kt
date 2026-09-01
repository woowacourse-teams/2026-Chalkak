package com.stonefive.chalkak.feature.signature

import com.stonefive.chalkak.core.ui.UiMessage

data class SignUpUiState(
    val status: SignUpStatus = SignUpStatus.Idle,
    val pendingMessage: UiMessage? = null,
) {
    val isSubmitting: Boolean
        get() = status == SignUpStatus.Submitting
}

sealed interface SignUpStatus {
    data object Idle : SignUpStatus

    data object Submitting : SignUpStatus

    data object Completed : SignUpStatus

    data object ReauthenticationRequired : SignUpStatus
}
