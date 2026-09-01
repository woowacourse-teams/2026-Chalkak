package com.stonefive.chalkak.feature.signature

data class SignUpUiState(val status: SignUpStatus = SignUpStatus.Idle) {
    val isSubmitting: Boolean
        get() = status == SignUpStatus.Submitting
}

sealed interface SignUpStatus {
    data object Idle : SignUpStatus

    data object Submitting : SignUpStatus

    data object Completed : SignUpStatus

    data object ReauthenticationRequired : SignUpStatus
}
