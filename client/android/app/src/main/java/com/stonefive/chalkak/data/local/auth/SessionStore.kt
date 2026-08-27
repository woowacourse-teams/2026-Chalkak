package com.stonefive.chalkak.data.local.auth

import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    val sessionState: StateFlow<UserSessionState>

    suspend fun continueAsGuest()

    suspend fun saveUserId(userId: String)

    suspend fun clear()
}
