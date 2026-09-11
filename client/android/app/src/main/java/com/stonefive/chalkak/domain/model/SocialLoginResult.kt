package com.stonefive.chalkak.domain.model

sealed interface SocialLoginResult {
    data class LoginSuccess(val userId: String) : SocialLoginResult

    data object SignUpRequired : SocialLoginResult

    data class Failure(val reason: SocialAuthFailure) : SocialLoginResult
}

enum class SocialAuthFailure {
    NETWORK_UNAVAILABLE,
    UNAUTHORIZED,
    UNSUPPORTED_PROVIDER,
    INVALID_RESPONSE,
    UNKNOWN,
}
