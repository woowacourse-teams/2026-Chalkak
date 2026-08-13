package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider

interface AuthRemoteDataSource {
    suspend fun login(provider: SocialLoginProvider): AuthResponse

    suspend fun continueAsGuest(): AuthResponse
}
