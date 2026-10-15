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
    private val sessionRefreshCoordinator: SessionRefreshCoordinator,
    private val json: Json,
) : Authenticator {
    constructor(
        sessionStore: SessionStore,
        tokenRefresher: TokenRefresher,
        json: Json,
    ) : this(
        sessionStore = sessionStore,
        sessionRefreshCoordinator = SessionRefreshCoordinator(sessionStore, tokenRefresher),
        json = json,
    )

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        val latest = (sessionStore.session.value as? LocalSession.Authenticated)?.credentials
            ?: return null

        val usedAccessToken = response.request
            .tag(AuthorizationRequestContext::class.java)
            ?.accessToken
        val requestUserId = response.request
            .tag(AuthorizationRequestContext::class.java)
            ?.userId

        if (requestUserId != null && requestUserId != latest.userId) return null

        if (usedAccessToken != null && usedAccessToken != latest.accessToken) {
            return response.request.withAccessToken(latest.accessToken, latest.userId)
        }

        if (response.requiresReauthentication()) {
            runBlocking { sessionStore.clear() }
            return null
        }

        return when (
            val result = runBlocking {
                sessionRefreshCoordinator.refreshAfterUnauthorized(
                    userId = latest.userId,
                    usedAccessToken = latest.accessToken,
                )
            }
        ) {
            is TokenRefreshResult.Success -> {
                val currentUserId = (sessionStore.session.value as? LocalSession.Authenticated)
                    ?.credentials
                    ?.userId
                if (currentUserId == result.credentials.userId) {
                    response.request.withAccessToken(
                        result.credentials.accessToken,
                        result.credentials.userId,
                    )
                } else {
                    null
                }
            }

            TokenRefreshResult.ReauthenticationRequired -> {
                val currentUserId = (sessionStore.session.value as? LocalSession.Authenticated)
                    ?.credentials
                    ?.userId
                if (currentUserId == latest.userId) runBlocking { sessionStore.clear() }
                null
            }

            TokenRefreshResult.TransientFailure -> null
        }
    }

    private fun Response.requiresReauthentication(): Boolean {
        val body = peekBody(MAX_ERROR_BODY_BYTES).string()
        val errorCode = runCatching { json.decodeFromString<ErrorResponse>(body).errorCode }.getOrNull()
        return errorCode == REAUTHENTICATION_REQUIRED
    }

    private fun Request.withAccessToken(
        accessToken: String,
        userId: String,
    ): Request = newBuilder()
        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
        .tag(
            AuthorizationRequestContext::class.java,
            AuthorizationRequestContext(accessToken, userId),
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
