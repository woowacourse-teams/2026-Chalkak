package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<UserSessionState>

    suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult

    suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult

    suspend fun continueAsGuest()

    suspend fun logout()

    suspend fun withdraw()
}
