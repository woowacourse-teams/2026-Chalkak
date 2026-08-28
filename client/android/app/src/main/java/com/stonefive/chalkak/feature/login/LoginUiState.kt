package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.domain.model.SocialLoginProvider

data class LoginUiState(
    val status: LoginStatus = LoginStatus.Idle,
    val activeProvider: SocialLoginProvider? = null,
) {
    val canSubmit: Boolean
        get() = status is LoginStatus.Idle || status is LoginStatus.Failed

    val errorMessage: String?
        get() = (status as? LoginStatus.Failed)?.message
}

sealed interface LoginStatus {
    data object Idle : LoginStatus

    data object Loading : LoginStatus

    data object Authenticated : LoginStatus

    data object GuestAccessGranted : LoginStatus

    data object SignUpRequired : LoginStatus

    data class Failed(val message: String) : LoginStatus
}
