package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.model.ErrorResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val sessionStore: SessionStore,
    private val tokenRefresher: TokenRefresher,
    private val json: Json,
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        synchronized(refreshLock) {
            val latest = (sessionStore.session.value as? LocalSession.Authenticated)?.credentials
                ?: return null

            val usedAccessToken = response.request
                .tag(AuthorizationRequestContext::class.java)
                ?.accessToken

            if (usedAccessToken != null && usedAccessToken != latest.accessToken) {
                return response.request.withAccessToken(latest.accessToken)
            }

            if (response.requiresReauthentication()) {
                runBlocking { sessionStore.clear() }
                return null
            }

            return when (
                val result = runBlocking { tokenRefresher.refresh(latest.userId, latest.refreshToken) }
            ) {
                is TokenRefreshResult.Success -> {
                    val applied = runBlocking { sessionStore.updateTokens(result.credentials) }
                    if (applied) {
                        response.request.withAccessToken(result.credentials.accessToken)
                    } else {
                        null
                    }
                }

                TokenRefreshResult.ReauthenticationRequired -> {
                    runBlocking { sessionStore.clear() }
                    null
                }

                TokenRefreshResult.TransientFailure -> null
            }
        }
    }

    private fun Response.requiresReauthentication(): Boolean {
        val body = peekBody(MAX_ERROR_BODY_BYTES).string()
        val errorCode = runCatching { json.decodeFromString<ErrorResponse>(body).errorCode }.getOrNull()
        return errorCode == REAUTHENTICATION_REQUIRED
    }

    private fun Request.withAccessToken(accessToken: String): Request = newBuilder()
        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
        .tag(
            AuthorizationRequestContext::class.java,
            AuthorizationRequestContext(accessToken),
        ).build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            count++
            priorResponse = priorResponse.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val REAUTHENTICATION_REQUIRED = "REAUTHENTICATION_REQUIRED"

        const val MAX_ATTEMPTS = 2
        const val MAX_ERROR_BODY_BYTES = 1_024L
    }
}
