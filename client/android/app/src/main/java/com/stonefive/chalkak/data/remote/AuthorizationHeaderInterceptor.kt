package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionStore
import java.time.Instant
import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationHeaderInterceptor(
    private val sessionStore: SessionStore,
    private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!originalRequest.url.isHttps) {
            return chain.proceed(
                originalRequest
                    .newBuilder()
                    .removeHeader(AUTHORIZATION_HEADER)
                    .build(),
            )
        }

        val credentials = (sessionStore.session.value as? LocalSession.Authenticated)?.credentials
        val request = originalRequest
            .newBuilder()
            .apply {
                if (credentials != null) {
                    tag(
                        AuthorizationRequestContext::class.java,
                        AuthorizationRequestContext(credentials.accessToken),
                    )
                    if (credentials.expiresAtEpochSeconds > currentEpochSeconds()) {
                        header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${credentials.accessToken}")
                    }
                }
            }.build()
        return chain.proceed(request)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}

data class AuthorizationRequestContext(val accessToken: String)
