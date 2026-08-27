package com.stonefive.chalkak.domain.model

sealed interface UserSessionState {
    data object Loading : UserSessionState

    data object SignedOut : UserSessionState

    data object Guest : UserSessionState

    data class Authenticated(val userId: String) : UserSessionState
}
