package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.UserProfile

interface AuthRepository {
    suspend fun login(provider: SocialLoginProvider): AuthSession.Authenticated

    suspend fun continueAsGuest(): AuthSession.Guest

    suspend fun getMyProfile(): UserProfile?

    suspend fun logout()

    suspend fun withdraw()
}
