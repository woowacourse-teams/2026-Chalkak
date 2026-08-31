package com.stonefive.chalkak.data.local.auth

import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    val session: StateFlow<LocalSession>
    val sessionState: StateFlow<UserSessionState>

    suspend fun continueAsGuest()

    suspend fun saveSession(credentials: SessionCredentials)

    suspend fun clear()

    suspend fun clearIfAccessTokenMatches(accessToken: String)
}

sealed interface LocalSession {
    data object Loading : LocalSession

    data object SignedOut : LocalSession

    data object Guest : LocalSession

    data class Authenticated(val credentials: SessionCredentials) : LocalSession
}

data class SessionCredentials(
    val userId: String,
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
)

fun LocalSession.toUserSessionState(): UserSessionState = when (this) {
    LocalSession.Loading -> UserSessionState.Loading
    LocalSession.SignedOut -> UserSessionState.SignedOut
    LocalSession.Guest -> UserSessionState.Guest
    is LocalSession.Authenticated -> UserSessionState.Authenticated(credentials.userId)
}
