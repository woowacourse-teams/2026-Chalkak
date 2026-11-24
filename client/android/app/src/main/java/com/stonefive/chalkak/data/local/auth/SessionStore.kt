package com.stonefive.chalkak.data.local.auth

import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    val session: StateFlow<LocalSession>
    val sessionState: StateFlow<UserSessionState>

    suspend fun continueAsGuest()

    suspend fun saveSession(credentials: SessionCredentials)

    suspend fun updateTokens(credentials: SessionCredentials): Boolean

    /** 사용자가 직접 로그아웃·탈퇴할 때. 토큰을 지우고 로그인 안 된 상태(SignedOut)가 된다. */
    suspend fun clear()

    /**
     * 서버가 Refresh 토큰을 거절해 다시 로그인해야 할 때. 저장된 토큰은 지우되 [userId]는 메모리에만 기억해
     * 재인증 필요 상태가 된다(같은 계정으로 다시 로그인하면 보던 화면으로 돌아가기 위해).
     * 지금 로그인한 계정이 [userId]가 아니면 아무것도 하지 않는다.
     */
    suspend fun requireReauthentication(userId: String)
}

sealed interface LocalSession {
    data object Loading : LocalSession

    data object SignedOut : LocalSession

    data object Guest : LocalSession

    data class Authenticated(val credentials: SessionCredentials) : LocalSession

    /** 토큰은 지웠고 [userId]만 기억한다. 앱을 다시 시작하면 SignedOut이 된다(디스크에 남기지 않음). */
    data class ReauthenticationRequired(val userId: String) : LocalSession
}

data class SessionCredentials(
    val userId: String,
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtEpochSeconds: Long,
)

fun LocalSession.toUserSessionState(): UserSessionState = when (this) {
    LocalSession.Loading -> UserSessionState.Loading
    LocalSession.SignedOut -> UserSessionState.SignedOut
    LocalSession.Guest -> UserSessionState.Guest
    is LocalSession.Authenticated -> UserSessionState.Authenticated(credentials.userId)
    is LocalSession.ReauthenticationRequired -> UserSessionState.ReauthenticationRequired(userId)
}
