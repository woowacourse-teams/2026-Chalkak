package com.stonefive.chalkak.domain.model

enum class UserProfileLoadFailure {
    UNAUTHORIZED,
    FORBIDDEN,
    NETWORK,
    UNKNOWN,
}

class UserProfileLoadException(val reason: UserProfileLoadFailure) : Exception()
