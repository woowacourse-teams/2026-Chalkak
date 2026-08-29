package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import okhttp3.Interceptor
import okhttp3.Response

class UserIdHeaderInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        val sessionState = sessionStore.sessionState.value
        if (sessionState is UserSessionState.Authenticated) {
            requestBuilder.header(USER_ID_HEADER, sessionState.userId)
            sessionStore.accessToken
                ?.takeIf(String::isNotBlank)
                ?.let { accessToken ->
                    requestBuilder.header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
                }
        }
        return chain.proceed(requestBuilder.build())
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val USER_ID_HEADER = "X-User-Id"
    }
}
