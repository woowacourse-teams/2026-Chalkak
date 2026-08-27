package com.stonefive.chalkak.feature.signature

data class SignUpUiState(val status: SignUpStatus = SignUpStatus.Idle) {
    val isSubmitting: Boolean
        get() = status == SignUpStatus.Submitting

    val errorMessage: String?
        get() = (status as? SignUpStatus.Failed)?.message
}

sealed interface SignUpStatus {
    data object Idle : SignUpStatus

    data object Submitting : SignUpStatus

    data object Completed : SignUpStatus

    data object ReauthenticationRequired : SignUpStatus

    data class Failed(val message: String) : SignUpStatus
}
