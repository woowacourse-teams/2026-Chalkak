package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.domain.model.SocialLoginProvider

data class LoginUiState(
    val status: LoginStatus = LoginStatus.Idle,
    val activeProvider: SocialLoginProvider? = null,
) {
    val canSubmit: Boolean
        get() = status is LoginStatus.Idle
}

sealed interface LoginStatus {
    data object Idle : LoginStatus

    data object Loading : LoginStatus

    data object Authenticated : LoginStatus

    data object GuestAccessGranted : LoginStatus

    data object SignUpRequired : LoginStatus
}
