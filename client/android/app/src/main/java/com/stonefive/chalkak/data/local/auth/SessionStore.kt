package com.stonefive.chalkak.data.local.auth

import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    val sessionState: StateFlow<UserSessionState>

    val accessToken: String?
        get() = null

    suspend fun continueAsGuest()

    suspend fun saveUserId(userId: String)

    suspend fun saveSession(
        userId: String,
        accessToken: String,
    ) {
        saveUserId(userId)
    }

    suspend fun clear()
}
