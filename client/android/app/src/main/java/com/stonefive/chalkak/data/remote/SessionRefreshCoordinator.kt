package com.stonefive.chalkak.data.remote

import android.util.Base64
import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface PushSessionReadiness {
    data class Ready(val credentials: SessionCredentials) : PushSessionReadiness

    data object NoSession : PushSessionReadiness

    data object ReauthenticationRequired : PushSessionReadiness

    data object TransientFailure : PushSessionReadiness

    data object SessionChanged : PushSessionReadiness
}

class SessionRefreshCoordinator(
    private val sessionStore: SessionStore,
    private val tokenRefresher: TokenRefresher,
) {
    private val refreshMutex = Mutex()

    suspend fun ensurePushRegistrationSession(expectedUserId: String): PushSessionReadiness = refreshMutex.withLock {
        val credentials = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?: return@withLock PushSessionReadiness.NoSession
        if (credentials.userId != expectedUserId) return@withLock PushSessionReadiness.SessionChanged

        if (credentials.accessToken.hasSessionIdClaim()) {
            return@withLock PushSessionReadiness.Ready(credentials)
        }

        when (val refreshed = refreshAndPersist(credentials)) {
            is TokenRefreshResult.Success -> {
                if (refreshed.credentials.userId != expectedUserId) {
                    PushSessionReadiness.SessionChanged
                } else if (refreshed.credentials.accessToken
                        .hasSessionIdClaim()
                ) {
                    PushSessionReadiness.Ready(refreshed.credentials)
                } else if (currentUserId() != expectedUserId) {
                    PushSessionReadiness.SessionChanged
                } else {
                    PushSessionReadiness.ReauthenticationRequired
                }
            }

            TokenRefreshResult.ReauthenticationRequired -> {
                if (currentUserId() == expectedUserId) {
                    PushSessionReadiness.ReauthenticationRequired
                } else {
                    PushSessionReadiness.SessionChanged
                }
            }

            TokenRefreshResult.TransientFailure -> PushSessionReadiness.TransientFailure
        }
    }

    suspend fun refreshAfterUnauthorized(
        userId: String,
        usedAccessToken: String,
    ): TokenRefreshResult = refreshMutex.withLock {
        val latestCredentials = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?: return@withLock TokenRefreshResult.ReauthenticationRequired
        if (latestCredentials.userId != userId) {
            return@withLock TokenRefreshResult.ReauthenticationRequired
        }
        if (latestCredentials.accessToken != usedAccessToken) {
            return@withLock TokenRefreshResult.Success(latestCredentials)
        }

        refreshAndPersist(latestCredentials)
    }

    private suspend fun refreshAndPersist(credentials: SessionCredentials): TokenRefreshResult =
        when (val result = tokenRefresher.refresh(credentials.userId, credentials.refreshToken)) {
            is TokenRefreshResult.Success -> {
                if (sessionStore.updateTokens(result.credentials)) {
                    TokenRefreshResult.Success(result.credentials)
                } else {
                    TokenRefreshResult.ReauthenticationRequired
                }
            }

            TokenRefreshResult.ReauthenticationRequired,
            TokenRefreshResult.TransientFailure,
            -> result
        }

    private fun currentUserId(): String? =
        (sessionStore.session.value as? LocalSession.Authenticated)?.credentials?.userId
}

private fun String.hasSessionIdClaim(): Boolean = runCatching {
    val payload = split('.').getOrNull(1) ?: return false
    val decodedPayload = Base64
        .decode(
            payload,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        ).decodeToString()
    val claim = Json
        .parseToJsonElement(decodedPayload)
        .jsonObject["session_id"]
        ?.jsonPrimitive
        ?.contentOrNull
    !claim.isNullOrBlank()
}.getOrDefault(false)
