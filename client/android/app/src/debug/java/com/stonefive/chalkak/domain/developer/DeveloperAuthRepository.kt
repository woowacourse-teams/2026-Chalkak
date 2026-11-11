package com.stonefive.chalkak.domain.developer

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 개발자 도구(debug 빌드 전용)에서 로그인 토큰을 확인하고 만료 상황을 일부러 만드는 기능.
 * 개발 서버에서 "토큰이 만료돼도 계속 쓸 수 있다", "갱신은 한 번만 일어난다" 같은 동작을 시연할 때 쓴다.
 * release 빌드에는 들어가지 않는다(src/debug).
 */
interface DeveloperAuthRepository {
    /** 지금 로그인한 세션의 토큰 만료 시각. 로그인하지 않았으면 null. */
    val tokenExpiry: Flow<AuthTokenExpiry?>

    /** 켜져 있으면 토큰 갱신 요청을 서버로 보내지 않고 네트워크 오류(IOException)로 끝낸다. */
    val isRefreshNetworkFailureEnabled: StateFlow<Boolean>

    /** 앱 프로세스가 켜진 뒤 성공한 토큰 갱신 횟수와 마지막 시각. 갱신 표시와 개발자 도구의 "마지막 갱신"에 쓴다. */
    val refreshHistory: StateFlow<TokenRefreshHistory>

    fun setRefreshNetworkFailureEnabled(enabled: Boolean)

    /** 저장된 Access 토큰만 서버가 거절할 무효 값으로 바꾼다. 로그인하지 않았으면 false. */
    suspend fun expireAccessToken(): Boolean

    /** 현재 Refresh 토큰으로 로그아웃 요청을 보내 서버에서 폐기하고, 로컬 세션은 둔 채 Access 토큰만 무효로 바꾼다. */
    suspend fun revokeRefreshToken(): RefreshTokenRevokeResult

    /**
     * Refresh 토큰은 지금 폐기하고, Access 토큰은 [delay] 뒤에 무효로 바꾼다. 개발자 도구를 닫고 원래 화면(예: 사진 올리기)으로
     * 돌아갈 시간을 주려고 쓴다. 타이머는 개발자 도구 화면을 닫아도 돈다. 그 사이 다른 계정으로 바뀌면 바꾸지 않는다.
     */
    suspend fun revokeRefreshTokenAndExpireAccessTokenAfter(delay: Duration): RefreshTokenRevokeResult

    /** 로그인이 필요한 요청을 [count]개 동시에 보내고, 그동안 일어난 토큰 갱신 요청 수와 성공 수를 돌려준다. */
    suspend fun sendConcurrentRequests(count: Int): ConcurrentRequestReport
}

/**
 * 만료 시각(epoch 초). 예: Access 2026-11-11 15:30:00 KST → 1_794_378_600.
 * [isAccessTokenInvalidated]: 저장된 Access 토큰이 개발자 도구가 넣은 무효 값이면 true(강제 만료됨).
 */
data class AuthTokenExpiry(
    val accessTokenExpiresAtEpochSeconds: Long,
    val refreshTokenExpiresAtEpochSeconds: Long,
    val isAccessTokenInvalidated: Boolean = false,
)

/** 성공한 토큰 갱신 [count]회, 마지막 갱신 시각 [lastRefreshedAtEpochSeconds](없으면 null). */
data class TokenRefreshHistory(
    val count: Int = 0,
    val lastRefreshedAtEpochSeconds: Long? = null,
)

sealed interface RefreshTokenRevokeResult {
    data object Success : RefreshTokenRevokeResult

    data object NoSession : RefreshTokenRevokeResult

    /** [httpCode]가 null이면 응답을 받지 못한 네트워크 오류다. */
    data class Failure(val httpCode: Int?) : RefreshTokenRevokeResult
}

data class ConcurrentRequestReport(
    val refreshCount: Int,
    val successCount: Int,
    val requestCount: Int,
)
