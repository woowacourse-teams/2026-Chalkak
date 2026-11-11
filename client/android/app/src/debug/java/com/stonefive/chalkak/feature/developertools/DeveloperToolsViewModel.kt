package com.stonefive.chalkak.feature.developertools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.developer.DeveloperAuthRepository
import com.stonefive.chalkak.domain.developer.RefreshTokenRevokeResult
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeveloperToolsUiState(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val applicationId: String,
    val serverEnvironment: String,
    val serverBaseUrl: String,
    val remoteConfig: AppConfigDiagnostics = AppConfigDiagnostics(),
    val isRefreshingRemoteConfig: Boolean = false,
    val auth: DeveloperAuthUiState = DeveloperAuthUiState(),
)

data class DeveloperAuthUiState(
    val isSignedIn: Boolean = false,
    val accessTokenExpiresAt: String? = null,
    val refreshTokenExpiresAt: String? = null,
    val isRefreshNetworkFailureEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val lastResult: String? = null,
)

private val kstFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))

private fun formatKst(epochSeconds: Long): String = kstFormatter.format(Instant.ofEpochSecond(epochSeconds))

class DeveloperToolsViewModel(
    private val appConfigRepository: AppConfigRepository,
    private val developerAuthRepository: DeveloperAuthRepository,
) : ViewModel() {
    private val buildInfo = DeveloperToolsUiState(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        applicationId = BuildConfig.APPLICATION_ID,
        serverEnvironment = URI(BuildConfig.API_BASE_URL).host,
        serverBaseUrl = BuildConfig.API_BASE_URL,
    )
    private val isRefreshing = MutableStateFlow(false)
    private val authAction = MutableStateFlow(AuthActionState())

    val uiState: StateFlow<DeveloperToolsUiState> =
        combine(
            appConfigRepository.diagnostics,
            isRefreshing,
            developerAuthRepository.tokenExpiry,
            developerAuthRepository.isRefreshNetworkFailureEnabled,
            authAction,
        ) { diagnostics, refreshing, expiry, networkFailure, action ->
            buildInfo.copy(
                remoteConfig = diagnostics,
                isRefreshingRemoteConfig = refreshing,
                auth = DeveloperAuthUiState(
                    isSignedIn = expiry != null,
                    accessTokenExpiresAt = expiry?.accessTokenExpiresAtEpochSeconds?.let(::formatKst),
                    refreshTokenExpiresAt = expiry?.refreshTokenExpiresAtEpochSeconds?.let(::formatKst),
                    isRefreshNetworkFailureEnabled = networkFailure,
                    isRunning = action.isRunning,
                    lastResult = action.lastResult,
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = buildInfo.copy(remoteConfig = appConfigRepository.diagnostics.value),
        )

    fun refreshRemoteConfig() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                appConfigRepository.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun expireAccessToken() = runAuthAction {
        if (developerAuthRepository.expireAccessToken()) ACCESS_TOKEN_EXPIRED else NOT_SIGNED_IN
    }

    fun revokeRefreshToken() = runAuthAction {
        when (val result = developerAuthRepository.revokeRefreshToken()) {
            RefreshTokenRevokeResult.Success -> REFRESH_TOKEN_REVOKED
            RefreshTokenRevokeResult.NoSession -> NOT_SIGNED_IN
            is RefreshTokenRevokeResult.Failure -> "폐기 요청 실패 (${result.httpCode ?: "네트워크"})"
        }
    }

    fun sendConcurrentRequests() = runAuthAction {
        val report = developerAuthRepository.sendConcurrentRequests(CONCURRENT_REQUEST_COUNT)
        "갱신 ${report.refreshCount}회 · 성공 ${report.successCount}/${report.requestCount}"
    }

    fun setRefreshNetworkFailureEnabled(enabled: Boolean) {
        developerAuthRepository.setRefreshNetworkFailureEnabled(enabled)
    }

    // 버튼 작업은 한 번에 하나만 하고, 끝나면 결과 한 줄을 남긴다.
    private fun runAuthAction(action: suspend () -> String) {
        if (authAction.value.isRunning) return
        authAction.update { it.copy(isRunning = true) }
        viewModelScope.launch {
            val result = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                "실패 (${error::class.simpleName})"
            }
            authAction.value = AuthActionState(isRunning = false, lastResult = result)
        }
    }

    private data class AuthActionState(
        val isRunning: Boolean = false,
        val lastResult: String? = null,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val CONCURRENT_REQUEST_COUNT = 5
        private const val ACCESS_TOKEN_EXPIRED = "Access 토큰을 무효 값으로 바꿨어요"
        private const val REFRESH_TOKEN_REVOKED = "Refresh 토큰을 폐기하고 Access 토큰을 무효로 바꿨어요"
        private const val NOT_SIGNED_IN = "로그인한 세션이 없어요"

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                DeveloperToolsViewModel(
                    appConfigRepository = application.appContainer.appConfigRepository,
                    developerAuthRepository = application.appContainer.buildVariantServices.developerAuthRepository,
                )
            }
        }
    }
}
