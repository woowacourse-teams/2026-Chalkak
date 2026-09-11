package com.stonefive.chalkak.domain.model

sealed interface SocialSignUpResult {
    data class Success(val userId: String) : SocialSignUpResult

    data class Failure(val reason: SocialSignUpFailure) : SocialSignUpResult
}

enum class SocialSignUpFailure {
    MISSING_LOGIN_CONTEXT,
    SIGNATURE_TOO_LARGE,
    SIGNATURE_PROCESSING_TIMEOUT,
    SIGNATURE_NOT_FOUND,
    INVALID_SIGNATURE,
    REAUTHENTICATION_REQUIRED,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}
