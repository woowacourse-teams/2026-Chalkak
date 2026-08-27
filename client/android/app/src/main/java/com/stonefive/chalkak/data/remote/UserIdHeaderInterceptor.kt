package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.model.UserSessionState
import okhttp3.Interceptor
import okhttp3.Response

// 추후 JWT로 변경 시 authorization 헤더로 변경 필요
class UserIdHeaderInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        val sessionState = sessionStore.sessionState.value
        if (sessionState is UserSessionState.Authenticated) {
            requestBuilder.header(USER_ID_HEADER, sessionState.userId)
        }
        return chain.proceed(requestBuilder.build())
    }

    private companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
