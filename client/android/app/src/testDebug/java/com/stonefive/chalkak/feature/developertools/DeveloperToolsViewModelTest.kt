package com.stonefive.chalkak.feature.developertools

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.notification.LocalNotificationPublisher
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.domain.developer.AuthTokenExpiry
import com.stonefive.chalkak.domain.developer.ConcurrentRequestReport
import com.stonefive.chalkak.domain.developer.DeveloperAuthRepository
import com.stonefive.chalkak.domain.developer.RefreshTokenRevokeResult
import com.stonefive.chalkak.domain.developer.TokenRefreshHistory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeDeveloperAuthRepository()

    @Test
    fun `테스트 알림을 보내면 화면 URI를 실은 화면 푸시를 띄운다`() {
        // Given  알림을 띄울 수 있다
        val publisher = FakeLocalNotificationPublisher(enabled = true)
        val viewModel = DeveloperToolsViewModel(FakeAppConfigRepository(), publisher, authRepository)

        // When   "알림 설정" 테스트 알림을 보낸다
        val sent = viewModel.sendTestNotification(DeveloperToolsTestNotification.NOTIFICATION_SETTINGS, "제목")

        // Then   chalkak://screen/notification-settings를 실은, notificationId 없는 알림 하나
        val published = publisher.published.single()
        assertTrue(sent)
        assertEquals("chalkak://screen/notification-settings", published.payload.screen)
        assertNull(published.payload.notificationId)
        assertTrue(
            published.payload.eventId!!
                .startsWith("developer-tools-"),
        )
        assertEquals("제목", published.title)
        assertEquals("chalkak://screen/notification-settings", published.body)
    }

    @Test
    fun `알림이 꺼져 있으면 보내지 못했다고 알린다`() {
        // Given  앱 알림이나 알림 채널이 꺼져 있다
        val viewModel = DeveloperToolsViewModel(
            FakeAppConfigRepository(),
            FakeLocalNotificationPublisher(enabled = false),
            authRepository,
        )

        // When   테스트 알림을 보낸다
        val sent = viewModel.sendTestNotification(DeveloperToolsTestNotification.RECORD, "제목")

        // Then   false(화면이 안내를 띄운다)
        assertFalse(sent)
    }

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

    @Test
    fun `Access 토큰 강제 만료 뒤 개발자 도구의 토큰 상태가 "무효(강제 만료됨)"로 보인다`() = runTest {
        // Given "u1" 로그인, AT 정상
        // When  Access 토큰 강제 만료
        // Then  상태 문구 "무효(강제 만료됨)" (저장된 AT가 무효 값인지는 DeveloperAuthRepositoryImplTest에서 본다)
        authRepository.tokenExpiry.value = AuthTokenExpiry(
            accessTokenExpiresAtEpochSeconds = kst(2026, 11, 11, 15, 30),
            refreshTokenExpiresAtEpochSeconds = kst(2026, 12, 11, 15, 15),
        )
        val viewModel = openDeveloperTools()

        viewModel.expireAccessToken()
        advanceUntilIdle()

        assertEquals("무효(강제 만료됨)", viewModel.uiState.value.auth.accessTokenStatus)
    }

    @Test
    fun `토큰이 갱신되면 상태가 정상으로 바뀌고 마지막 갱신 시각과 누적 횟수가 보인다`() = runTest {
        // Given 강제 만료 상태, 지금 18:04:10 KST
        // When  일반 API가 401을 받고 갱신 성공 (저장소가 새 만료 시각·갱신 기록을 내보냄)
        // Then  상태 "정상", 마지막 갱신 "18:04:10 갱신됨 (누적 1회)", AT 만료 시각이 새 값으로 바뀜
        authRepository.tokenExpiry.value = AuthTokenExpiry(
            accessTokenExpiresAtEpochSeconds = kst(2026, 11, 11, 15, 30),
            refreshTokenExpiresAtEpochSeconds = kst(2026, 12, 11, 15, 15),
            isAccessTokenInvalidated = true,
        )
        val viewModel = openDeveloperTools()

        authRepository.tokenExpiry.value = AuthTokenExpiry(
            accessTokenExpiresAtEpochSeconds = kst(2026, 11, 11, 18, 19) + 10,
            refreshTokenExpiresAtEpochSeconds = kst(2026, 12, 11, 18, 4) + 10,
        )
        authRepository.refreshHistory.value = TokenRefreshHistory(
            count = 1,
            lastRefreshedAtEpochSeconds = kst(2026, 11, 11, 18, 4) + 10,
        )
        advanceUntilIdle()

        assertEquals("정상", viewModel.uiState.value.auth.accessTokenStatus)
        assertEquals("18:04:10 갱신됨 (누적 1회)", viewModel.uiState.value.auth.lastRefresh)
        assertEquals("2026-11-11 18:19:10", viewModel.uiState.value.auth.accessTokenExpiresAt)
    }

    private fun TestScope.openDeveloperTools(): DeveloperToolsViewModel {
        val viewModel = DeveloperToolsViewModel(
            FakeAppConfigRepository(),
            FakeLocalNotificationPublisher(enabled = true),
            authRepository,
        )
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
    override val refreshHistory = MutableStateFlow(TokenRefreshHistory())
    var revokeResult: RefreshTokenRevokeResult = RefreshTokenRevokeResult.Success
    var concurrentReport = ConcurrentRequestReport(0, 0, 0)
    val concurrentRequestCounts = mutableListOf<Int>()

    override fun setRefreshNetworkFailureEnabled(enabled: Boolean) {
        failureSwitch.value = enabled
    }

    override suspend fun expireAccessToken(): Boolean {
        tokenExpiry.value = tokenExpiry.value?.copy(isAccessTokenInvalidated = true)
        return true
    }

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

private data class Published(
    val payload: NotificationPushData,
    val title: String,
    val body: String,
)

private class FakeLocalNotificationPublisher(private val enabled: Boolean) : LocalNotificationPublisher {
    val published = mutableListOf<Published>()

    override fun publish(
        payload: NotificationPushData,
        title: String,
        body: String,
    ): Boolean {
        if (!enabled) return false
        published += Published(payload, title, body)
        return true
    }
}
