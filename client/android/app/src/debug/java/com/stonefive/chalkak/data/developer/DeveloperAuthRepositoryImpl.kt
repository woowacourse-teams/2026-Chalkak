package com.stonefive.chalkak.data.developer

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.auth.model.request.LogoutRequest
import com.stonefive.chalkak.domain.developer.AuthTokenExpiry
import com.stonefive.chalkak.domain.developer.ConcurrentRequestReport
import com.stonefive.chalkak.domain.developer.DeveloperAuthRepository
import com.stonefive.chalkak.domain.developer.RefreshTokenRevokeResult
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 개발자 도구의 인증 기능(debug 전용).
 *
 * - [authenticatedClient]: 앱이 실제로 쓰는 로그인 클라이언트(토큰 헤더 + 401이면 갱신). 동시 요청을 여기로 보낸다.
 * - [baseUrl]: API 기준 주소. 예: https://dev.example.com/api/v1/
 * - [refreshRequestProbe]: 토큰 갱신 클라이언트에 붙은 장치. 갱신 요청 수와 네트워크 실패 스위치를 가진다.
 * - [plainClient]: 로그아웃(Refresh 토큰 폐기) 요청용. Access 토큰이 필요 없고 갱신이 끼어들면 안 되므로 따로 둔다.
 */
class DeveloperAuthRepositoryImpl(
    private val sessionStore: SessionStore,
    private val authenticatedClient: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val refreshRequestProbe: RefreshRequestProbe,
    private val plainClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeveloperAuthRepository {
    override val tokenExpiry: Flow<AuthTokenExpiry?> = sessionStore.session
        .map { session ->
            (session as? LocalSession.Authenticated)?.credentials?.let { credentials ->
                AuthTokenExpiry(
                    accessTokenExpiresAtEpochSeconds = credentials.expiresAtEpochSeconds,
                    refreshTokenExpiresAtEpochSeconds = credentials.refreshTokenExpiresAtEpochSeconds,
                )
            }
        }.distinctUntilChanged()

    override val isRefreshNetworkFailureEnabled: StateFlow<Boolean> = refreshRequestProbe.isNetworkFailureEnabled

    override fun setRefreshNetworkFailureEnabled(enabled: Boolean) {
        refreshRequestProbe.setNetworkFailureEnabled(enabled)
    }

    override suspend fun expireAccessToken(): Boolean {
        val credentials = currentCredentials() ?: return false
        return sessionStore.updateTokens(credentials.withInvalidAccessToken())
    }

    override suspend fun revokeRefreshToken(): RefreshTokenRevokeResult {
        val credentials = currentCredentials() ?: return RefreshTokenRevokeResult.NoSession
        val request = Request
            .Builder()
            .url(baseUrl.resolve(LOGOUT_PATH) ?: return RefreshTokenRevokeResult.Failure(null))
            .post(json.encodeToString(LogoutRequest(credentials.refreshToken)).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val code = try {
            withContext(ioDispatcher) {
                plainClient
                    .newCall(request)
                    .execute()
                    .use { it.code }
            }
        } catch (_: IOException) {
            return RefreshTokenRevokeResult.Failure(null)
        }
        if (code !in HTTP_SUCCESS) return RefreshTokenRevokeResult.Failure(code)
        // 서버의 Refresh 토큰은 폐기됐다. Access 토큰을 무효로 바꿔 다음 요청에서 갱신 → 재인증 필요가 바로 일어나게 한다.
        // 로컬 Refresh 토큰은 그대로 둔다(그 토큰으로 갱신해야 서버가 REAUTHENTICATION_REQUIRED를 준다).
        currentCredentials()
            ?.takeIf { it.userId == credentials.userId }
            ?.let { sessionStore.updateTokens(it.withInvalidAccessToken()) }
        return RefreshTokenRevokeResult.Success
    }

    override suspend fun sendConcurrentRequests(count: Int): ConcurrentRequestReport {
        val url = baseUrl.resolve(AUTHENTICATED_PROBE_PATH) ?: return ConcurrentRequestReport(0, 0, count)
        val refreshCountBefore = refreshRequestProbe.refreshRequestCount
        val results = coroutineScope {
            List(count) {
                async(ioDispatcher) {
                    try {
                        authenticatedClient
                            .newCall(
                                Request
                                    .Builder()
                                    .url(url)
                                    .build(),
                            ).execute()
                            .use { it.code in HTTP_SUCCESS }
                    } catch (_: IOException) {
                        false
                    }
                }
            }.awaitAll()
        }
        return ConcurrentRequestReport(
            refreshCount = refreshRequestProbe.refreshRequestCount - refreshCountBefore,
            successCount = results.count { it },
            requestCount = count,
        )
    }

    private fun currentCredentials(): SessionCredentials? =
        (sessionStore.session.value as? LocalSession.Authenticated)?.credentials

    private fun SessionCredentials.withInvalidAccessToken() = copy(accessToken = INVALID_ACCESS_TOKEN)

    private companion object {
        // 서버가 서명 검증에 실패해 401 UNAUTHORIZED를 주는 값.
        const val INVALID_ACCESS_TOKEN = "developer-tools-invalid-access-token"
        const val LOGOUT_PATH = "auth/logout"

        // 로그인이 필요한 가벼운 조회. 동시 요청 시험에 쓴다.
        const val AUTHENTICATED_PROBE_PATH = "users/me/signature"
        val HTTP_SUCCESS = 200..299
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val json = Json
    }
}
