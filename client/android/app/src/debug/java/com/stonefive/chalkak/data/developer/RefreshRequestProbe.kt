package com.stonefive.chalkak.data.developer

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 토큰 갱신 요청 전용 OkHttp 클라이언트에 붙는 debug 전용 장치.
 * - 서버로 보낸 갱신 요청 수를 센다(개발자 도구의 "갱신 1회" 표시).
 * - 네트워크 실패 스위치가 켜져 있으면 요청을 보내지 않고 IOException을 던진다(갱신 중 네트워크가 끊긴 상황 재현).
 */
class RefreshRequestProbe : Interceptor {
    private val sentRefreshRequests = AtomicInteger(0)
    private val networkFailure = MutableStateFlow(false)

    val refreshRequestCount: Int get() = sentRefreshRequests.get()
    val isNetworkFailureEnabled: StateFlow<Boolean> = networkFailure.asStateFlow()

    fun setNetworkFailureEnabled(enabled: Boolean) {
        networkFailure.value = enabled
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (networkFailure.value) throw IOException("개발자 도구: 갱신 요청 네트워크 실패 스위치가 켜져 있음")
        sentRefreshRequests.incrementAndGet()
        return chain.proceed(chain.request())
    }
}
