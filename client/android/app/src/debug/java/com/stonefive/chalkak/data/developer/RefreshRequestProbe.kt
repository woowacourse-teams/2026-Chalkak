package com.stonefive.chalkak.data.developer

import com.stonefive.chalkak.domain.developer.TokenRefreshHistory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 토큰 갱신 요청 전용 OkHttp 클라이언트에 붙는 debug 전용 장치.
 * - 서버로 보낸 갱신 요청 수를 센다(개발자 도구의 "갱신 1회" 표시).
 * - 성공한 갱신의 횟수와 시각을 남긴다(메인 화면의 갱신 표시, 개발자 도구의 "마지막 갱신"). 앱 프로세스가 켜진 뒤부터 센다.
 * - 네트워크 실패 스위치가 켜져 있으면 요청을 보내지 않고 IOException을 던진다(갱신 중 네트워크가 끊긴 상황 재현).
 */
class RefreshRequestProbe(private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond }) : Interceptor {
    private val history = MutableStateFlow(TokenRefreshHistory())
    val refreshHistory: StateFlow<TokenRefreshHistory> = history.asStateFlow()

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
        val response = chain.proceed(chain.request())
        // 서버가 새 토큰을 준 갱신만 센다. 여러 요청이 동시에 401을 받아도 갱신 요청은 하나라 한 번만 늘어난다.
        if (response.isSuccessful) {
            history.update {
                TokenRefreshHistory(
                    count = it.count + 1,
                    lastRefreshedAtEpochSeconds = currentEpochSeconds(),
                )
            }
        }
        return response
    }
}
