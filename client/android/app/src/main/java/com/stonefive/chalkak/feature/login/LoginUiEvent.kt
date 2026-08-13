package com.stonefive.chalkak.feature.login

sealed interface LoginUiEvent {
    data object NavigateToHome : LoginUiEvent
}
