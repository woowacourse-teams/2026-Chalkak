package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import kotlinx.coroutines.delay

class MockAuthRemoteDataSource(private val responseDelayMillis: Long = 500L) : AuthRemoteDataSource {
    override suspend fun login(provider: SocialLoginProvider): AuthResponse {
        delay(responseDelayMillis)
        return AuthResponse(
            provider = provider.name,
            isGuest = false,
        )
    }

    override suspend fun continueAsGuest(): AuthResponse {
        delay(responseDelayMillis)
        return AuthResponse(
            provider = null,
            isGuest = true,
        )
    }
}
