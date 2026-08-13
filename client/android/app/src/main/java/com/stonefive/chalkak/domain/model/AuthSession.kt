package com.stonefive.chalkak.domain.model

sealed interface AuthSession {
    data class Authenticated(val provider: SocialLoginProvider) : AuthSession

    data object Guest : AuthSession
}
