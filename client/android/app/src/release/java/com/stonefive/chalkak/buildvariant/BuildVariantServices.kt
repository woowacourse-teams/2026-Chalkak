package com.stonefive.chalkak.buildvariant

import com.stonefive.chalkak.data.local.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient

// release 빌드에는 개발자 도구가 없으므로 토큰 갱신 클라이언트에 아무것도 붙이지 않는다.
fun buildVariantRefreshInterceptors(): List<Interceptor> = emptyList()

@Suppress("UNUSED_PARAMETER")
class BuildVariantServices(
    sessionStore: SessionStore,
    authenticatedClient: OkHttpClient,
    baseUrl: String,
)
