package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.data.remote.auth.model.ProfileResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import kotlinx.coroutines.delay

class MockAuthRemoteDataSource(private val responseDelayMillis: Long = 500L) : AuthRemoteDataSource {
    private var profile: ProfileResponse? = null

    override suspend fun login(provider: SocialLoginProvider): AuthResponse {
        delay(responseDelayMillis)
        profile = ProfileResponse(signatureUrl = null)
        return AuthResponse(
            provider = provider.name,
            isGuest = false,
        )
    }

    override suspend fun continueAsGuest(): AuthResponse {
        delay(responseDelayMillis)
        profile = null
        return AuthResponse(
            provider = null,
            isGuest = true,
        )
    }

    override suspend fun getMyProfile(): ProfileResponse? {
        delay(responseDelayMillis)
        return profile
    }

    override suspend fun logout() {
        delay(responseDelayMillis)
        profile = null
    }

    override suspend fun withdraw() {
        delay(responseDelayMillis)
        profile = null
    }
}
