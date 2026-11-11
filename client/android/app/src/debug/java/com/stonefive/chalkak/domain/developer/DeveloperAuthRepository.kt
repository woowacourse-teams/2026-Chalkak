package com.stonefive.chalkak.domain.developer

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

    fun setRefreshNetworkFailureEnabled(enabled: Boolean)

    /** 저장된 Access 토큰만 서버가 거절할 무효 값으로 바꾼다. 로그인하지 않았으면 false. */
    suspend fun expireAccessToken(): Boolean

    /** 현재 Refresh 토큰으로 로그아웃 요청을 보내 서버에서 폐기하고, 로컬 세션은 둔 채 Access 토큰만 무효로 바꾼다. */
    suspend fun revokeRefreshToken(): RefreshTokenRevokeResult

    /** 로그인이 필요한 요청을 [count]개 동시에 보내고, 그동안 일어난 토큰 갱신 요청 수와 성공 수를 돌려준다. */
    suspend fun sendConcurrentRequests(count: Int): ConcurrentRequestReport
}

/** 만료 시각(epoch 초). 예: Access 2026-11-11 15:30:00 KST → 1_794_378_600. */
data class AuthTokenExpiry(
    val accessTokenExpiresAtEpochSeconds: Long,
    val refreshTokenExpiresAtEpochSeconds: Long,
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
