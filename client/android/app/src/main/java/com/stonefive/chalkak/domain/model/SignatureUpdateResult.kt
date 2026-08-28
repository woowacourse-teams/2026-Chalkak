package com.stonefive.chalkak.domain.model

sealed interface SignatureUpdateResult {
    data class Success(val profile: UserProfile) : SignatureUpdateResult

    data class Failure(val reason: SignatureUpdateFailure) : SignatureUpdateResult
}

enum class SignatureUpdateFailure {
    SIGNATURE_TOO_LARGE,
    INVALID_SIGNATURE,
    NETWORK_UNAVAILABLE,
    REAUTHENTICATION_REQUIRED,
    SIGNATURE_NOT_FOUND,
    UNKNOWN,
}
