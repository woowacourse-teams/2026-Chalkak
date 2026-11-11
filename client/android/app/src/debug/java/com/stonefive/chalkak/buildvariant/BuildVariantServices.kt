package com.stonefive.chalkak.buildvariant

import com.stonefive.chalkak.data.developer.DeveloperAuthRepositoryImpl
import com.stonefive.chalkak.data.developer.RefreshRequestProbe
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.domain.developer.DeveloperAuthRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

// 갱신 요청 수 세기와 네트워크 실패 스위치는 앱 전체에서 하나만 둔다(갱신 클라이언트와 개발자 도구가 같은 것을 본다).
private val refreshRequestProbe = RefreshRequestProbe()

/** debug 빌드에서만 토큰 갱신 클라이언트에 [RefreshRequestProbe]를 붙인다. release는 빈 목록이다. */
fun buildVariantRefreshInterceptors(): List<Interceptor> = listOf(refreshRequestProbe)

/** debug 빌드 전용 기능(개발자 도구)이 쓰는 의존성. release에는 같은 이름의 빈 클래스가 있다. */
class BuildVariantServices(
    sessionStore: SessionStore,
    authenticatedClient: OkHttpClient,
    baseUrl: String,
) {
    val developerAuthRepository: DeveloperAuthRepository by lazy {
        DeveloperAuthRepositoryImpl(
            sessionStore = sessionStore,
            authenticatedClient = authenticatedClient,
            baseUrl = baseUrl.toHttpUrl(),
            refreshRequestProbe = refreshRequestProbe,
        )
    }
}
