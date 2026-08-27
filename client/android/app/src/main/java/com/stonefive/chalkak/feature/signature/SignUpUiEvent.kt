package com.stonefive.chalkak.feature.signature

sealed interface SignUpUiEvent {
    data object NavigateToLogin : SignUpUiEvent
}
