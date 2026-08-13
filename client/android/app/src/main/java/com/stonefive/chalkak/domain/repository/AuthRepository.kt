package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider

interface AuthRepository {
    suspend fun login(provider: SocialLoginProvider): AuthSession.Authenticated

    suspend fun continueAsGuest(): AuthSession.Guest
}
