package com.stonefive.chalkak.feature.developertools

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.developer.AuthTokenExpiry
import com.stonefive.chalkak.domain.developer.ConcurrentRequestReport
import com.stonefive.chalkak.domain.developer.DeveloperAuthRepository
import com.stonefive.chalkak.domain.developer.RefreshTokenRevokeResult
import com.stonefive.chalkak.domain.model.AppConfig
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.HomeIssueExperiment
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeDeveloperAuthRepository()

    @Test
    fun `개발자 도구가 Access·Refresh 토큰 만료 시각을 한국 시간으로 보여 준다`() = runTest {
        // Given AT 만료 2026-11-11 15:30:00 KST, RT 만료 2026-12-11 15:15:00 KST인 세션
        // When  개발자 도구를 연다
        // Then  화면 상태 "2026-11-11 15:30:00", "2026-12-11 15:15:00"
        authRepository.tokenExpiry.value = AuthTokenExpiry(
            accessTokenExpiresAtEpochSeconds = kst(2026, 11, 11, 15, 30),
            refreshTokenExpiresAtEpochSeconds = kst(2026, 12, 11, 15, 15),
        )

        val viewModel = openDeveloperTools()

        assertEquals("2026-11-11 15:30:00", viewModel.uiState.value.auth.accessTokenExpiresAt)
        assertEquals("2026-12-11 15:15:00", viewModel.uiState.value.auth.refreshTokenExpiresAt)
    }

    @Test
    fun `Refresh 토큰 폐기 요청이 실패하면 토큰을 바꾸지 않고 실패를 알린다`() = runTest {
        // Given logout 요청이 500
        // When  Refresh 토큰 폐기
        // Then  결과 문구 "폐기 요청 실패 (500)" (AT 그대로는 DeveloperAuthRepositoryImplTest에서 본다)
        authRepository.revokeResult = RefreshTokenRevokeResult.Failure(500)
        val viewModel = openDeveloperTools()

        viewModel.revokeRefreshToken()
        advanceUntilIdle()

        assertEquals("폐기 요청 실패 (500)", viewModel.uiState.value.auth.lastResult)
    }

    @Test
    fun `Refresh 토큰 폐기(30초 뒤 만료)를 누르면 지금은 로그아웃 요청만 보내고 AT는 30초 뒤 무효로 바뀐다`() = runTest {
        // Given "u1", AT "at-1", RT "rt-1"
        // When  Refresh 토큰 폐기(30초 뒤 만료)
        // Then  결과 문구 "Refresh 토큰을 폐기했어요. 30초 뒤 Access 토큰이 만료돼요"
        //       (logout 요청·AT 바뀌는 시점은 DeveloperAuthRepositoryImplTest에서 본다)
        val viewModel = openDeveloperTools()

        viewModel.revokeRefreshTokenWithDelayedExpiry()
        advanceUntilIdle()

        assertEquals(listOf(30.seconds), authRepository.delayedExpiryRequests)
        assertEquals("Refresh 토큰을 폐기했어요. 30초 뒤 Access 토큰이 만료돼요", viewModel.uiState.value.auth.lastResult)
    }

    @Test
    fun `동시 요청 5개를 보내면 갱신 횟수와 성공 수를 보여 준다`() = runTest {
        // Given AT 무효, 서버는 무효 AT에 401·갱신 요청에 새 토큰 (저장소가 갱신 1회, 성공 5를 돌려줌)
        // When  동시 요청 5개
        // Then  화면 상태 "갱신 1회 · 성공 5/5"
        authRepository.concurrentReport = ConcurrentRequestReport(refreshCount = 1, successCount = 5, requestCount = 5)
        val viewModel = openDeveloperTools()

        viewModel.sendConcurrentRequests()
        advanceUntilIdle()

        assertEquals(listOf(5), authRepository.concurrentRequestCounts)
        assertEquals("갱신 1회 · 성공 5/5", viewModel.uiState.value.auth.lastResult)
    }

    private fun TestScope.openDeveloperTools(): DeveloperToolsViewModel {
        val viewModel = DeveloperToolsViewModel(FakeAppConfigRepository(), authRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun kst(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toEpochSecond()
}

private class FakeDeveloperAuthRepository : DeveloperAuthRepository {
    override val tokenExpiry = MutableStateFlow<AuthTokenExpiry?>(null)
    private val failureSwitch = MutableStateFlow(false)
    override val isRefreshNetworkFailureEnabled: StateFlow<Boolean> = failureSwitch
    var revokeResult: RefreshTokenRevokeResult = RefreshTokenRevokeResult.Success
    var concurrentReport = ConcurrentRequestReport(0, 0, 0)
    val concurrentRequestCounts = mutableListOf<Int>()

    override fun setRefreshNetworkFailureEnabled(enabled: Boolean) {
        failureSwitch.value = enabled
    }

    override suspend fun expireAccessToken(): Boolean = true

    override suspend fun revokeRefreshToken(): RefreshTokenRevokeResult = revokeResult

    val delayedExpiryRequests = mutableListOf<Duration>()

    override suspend fun revokeRefreshTokenAndExpireAccessTokenAfter(delay: Duration): RefreshTokenRevokeResult {
        delayedExpiryRequests += delay
        return revokeResult
    }

    override suspend fun sendConcurrentRequests(count: Int): ConcurrentRequestReport {
        concurrentRequestCounts += count
        return concurrentReport
    }
}

private class FakeAppConfigRepository : AppConfigRepository {
    override val config: StateFlow<AppConfig> = MutableStateFlow(AppConfig())
    override val diagnostics: StateFlow<AppConfigDiagnostics> = MutableStateFlow(AppConfigDiagnostics())

    override suspend fun refresh() = Unit

    override suspend fun homeIssueExperiment(): HomeIssueExperiment = HomeIssueExperiment()
}
