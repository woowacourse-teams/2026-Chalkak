package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.model.ErrorResponse
import java.io.IOException
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
            runBlocking { sessionStore.requireReauthentication(latest.userId) }
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
                if (currentUserId == latest.userId) {
                    runBlocking { sessionStore.requireReauthentication(latest.userId) }
                }
                null
            }

            // 갱신 중 네트워크가 끊기면 401을 그대로 넘기지 않는다. 화면이 401을 "다시 로그인"으로 바꿔
            // 살아 있는 로그인을 로그인 화면으로 보내지 않도록, 원요청을 네트워크 오류로 끝낸다.
            TokenRefreshResult.TransientFailure -> throw IOException("토큰 갱신이 네트워크 오류로 실패함")
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
