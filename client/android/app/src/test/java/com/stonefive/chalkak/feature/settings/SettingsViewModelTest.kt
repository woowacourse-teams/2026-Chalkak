package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.NotificationRepository
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeSettingsAuthRepository()
    private val userRepository = FakeSettingsUserRepository(authRepository::setSignedOut)

    @Test
    fun `프로필이 있으면 로그인 상태와 서명을 제공한다`() = runTest {
        authRepository.setAuthenticated()
        userRepository.profile = UserProfile(
            signatureUrl = "original-signature-url",
            signatureThumbnailUrl = "thumbnail-signature-url",
        )

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals("thumbnail-signature-url", viewModel.uiState.value.signatureUrl)
        assertEquals("1.2.4", viewModel.uiState.value.versionName)
    }

    @Test
    fun `설정 재진입 시 최신 서명을 다시 조회한다`() = runTest {
        authRepository.setAuthenticated()
        val viewModel = createViewModel()
        userRepository.profile = UserProfile("new-original", "new-thumbnail")

        viewModel.refreshSignature()
        advanceUntilIdle()

        assertEquals(2, userRepository.getMySignatureCalled)
        assertEquals("new-thumbnail", viewModel.uiState.value.signatureUrl)
    }

    @Test
    fun `서명 갱신 중 중복 요청을 막고 실패해도 기존 서명을 유지한다`() = runTest {
        authRepository.setAuthenticated()
        val viewModel = createViewModel()
        val previousSignature = viewModel.uiState.value.signatureUrl
        val gate = CompletableDeferred<Unit>()
        userRepository.profileAwait = gate
        userRepository.profileError = IllegalStateException("failure")

        viewModel.refreshSignature()
        viewModel.refreshSignature()
        assertEquals(2, userRepository.getMySignatureCalled)
        assertEquals(previousSignature, viewModel.uiState.value.signatureUrl)
        assertFalse(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(previousSignature, viewModel.uiState.value.signatureUrl)
        assertTrue(viewModel.uiState.value.pendingMessage is UiMessage.Toast)
    }

    @Test
    fun `서명 조회가 401이면 로그아웃 상태를 제공한다`() = runTest {
        authRepository.setAuthenticated()
        userRepository.profileError = UserProfileLoadException(UserProfileLoadFailure.UNAUTHORIZED)

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(null, viewModel.uiState.value.signatureUrl)
    }

    @Test
    fun `프로필이 없으면 비회원 상태를 제공한다`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(0, userRepository.getMySignatureCalled)
    }

    @Test
    fun `일반 서명 조회 실패는 설정을 유지하고 Toast 메시지를 보낸다`() = runTest {
        authRepository.setAuthenticated()
        val gate = CompletableDeferred<Unit>()
        userRepository.profileAwait = gate
        userRepository.profileError = IllegalStateException("failure")
        val viewModel = createViewModel()

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            "사인을 불러오지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `서명 조회가 인증 외 오류면 로그인 상태를 유지하고 Toast 메시지를 보낸다`() = runTest {
        authRepository.setAuthenticated()
        val gate = CompletableDeferred<Unit>()
        userRepository.profileAwait = gate
        userRepository.profileError = UserProfileLoadException(UserProfileLoadFailure.NETWORK)
        val viewModel = createViewModel()

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            "사인을 불러오지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `계정 작업 실패 시 로그인 상태를 유지하고 Toast 메시지를 보낸다`() = runTest {
        authRepository.setAuthenticated()
        authRepository.logoutError = IllegalStateException("failure")
        val viewModel = createViewModel()
        viewModel.showLogoutDialog()

        viewModel.confirmAccountAction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(authRepository.logoutCalled)
        assertEquals(
            "요청을 처리하지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `로그아웃 API 성공 후 로그인 화면으로 이동한다`() = runTest {
        authRepository.setAuthenticated()
        val viewModel = createViewModel()

        viewModel.showLogoutDialog()

        assertFalse(authRepository.logoutCalled)

        viewModel.confirmAccountAction()

        assertTrue(authRepository.logoutCalled)
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(UserSessionState.SignedOut, authRepository.sessionState.value)
    }

    @Test
    fun `계정 작업 다이얼로그를 취소하면 API를 호출하지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.showLogoutDialog()
        viewModel.dismissAccountDialog()

        assertEquals(null, viewModel.uiState.value.accountDialog)
        assertFalse(authRepository.logoutCalled)
    }

    @Test
    fun `회원탈퇴 확인 시 회원탈퇴 API를 호출한다`() = runTest {
        authRepository.setAuthenticated()
        val viewModel = createViewModel()

        viewModel.showWithdrawDialog()
        viewModel.confirmAccountAction()

        assertTrue(userRepository.withdrawCalled)
        assertEquals(UserSessionState.SignedOut, authRepository.sessionState.value)
    }

    @Test
    fun `게스트가 로그인을 시작하면 게스트 세션을 해제한다`() = runTest {
        val viewModel = createViewModel()
        assertEquals(UserSessionState.Guest, authRepository.sessionState.value)

        viewModel.startLogin()
        advanceUntilIdle()

        assertTrue(authRepository.logoutCalled)
        assertEquals(UserSessionState.SignedOut, authRepository.sessionState.value)
    }

    @Test
    fun `개별 토글은 선택한 알림만 변경한다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository()
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        advanceUntilIdle()
        assertEquals(NotificationPushSettingsUpdate(topicPushEnabled = false), repository.updates.last())
        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)

        viewModel.updateModerationPushEnabled(false)
        advanceUntilIdle()
        assertEquals(NotificationPushSettingsUpdate(moderationPushEnabled = false), repository.updates.last())
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)

        viewModel.updateTopicPushEnabled(true)
        advanceUntilIdle()
        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)

        viewModel.updateModerationPushEnabled(true)
        advanceUntilIdle()
        assertEquals(NotificationPushSettings(true, true), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `개별 설정 저장 실패 시 실패한 설정만 복원한다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            settings = NotificationPushSettings(true, false)
            updateResult = NotificationResult.Failure(NotificationFailure.Http(500, null))
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateModerationPushEnabled(true)
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
        assertTrue(viewModel.uiState.value.pendingMessage != null)
    }

    @Test
    fun `다른 설정은 저장 중이어도 각각 요청한다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            suspendUpdates = true
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)

        assertEquals(
            listOf(
                NotificationPushSettingsUpdate(topicPushEnabled = false),
                NotificationPushSettingsUpdate(moderationPushEnabled = false),
            ),
            repository.updates,
        )
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)
        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertTrue(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(0, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
        assertEquals(NotificationPushSettings(false, false), viewModel.uiState.value.pushSettings)
    }

    @Test
    fun `같은 설정 저장 중 반복 입력은 추가 요청하지 않는다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            suspendUpdates = true
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateTopicPushEnabled(true)

        assertEquals(listOf(NotificationPushSettingsUpdate(topicPushEnabled = false)), repository.updates)
        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)
        assertTrue(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)

        repository.completeOnlyPendingUpdate(NotificationResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
    }

    @Test
    fun `한 설정 저장 실패는 진행 중인 다른 설정 값을 되돌리지 않는다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            suspendUpdates = true
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertTrue(viewModel.uiState.value.isModerationPushSaving)

        repository.completePendingUpdate(1, NotificationResult.Success(Unit))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, false), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }

    @Test
    fun `나중 요청이 실패해도 먼저 성공한 다른 설정 값은 유지한다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            suspendUpdates = true
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Success(Unit))
        advanceUntilIdle()
        repository.completePendingUpdate(1, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(false, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }

    @Test
    fun `두 설정 저장이 모두 실패하면 각 설정을 기존 값으로 복원한다`() = runTest {
        authRepository.setAuthenticated()
        val repository = FakeSettingsNotificationRepository().apply {
            suspendUpdates = true
        }
        val viewModel = SettingsViewModel(authRepository, userRepository, "1.2.4", repository)
        advanceUntilIdle()

        viewModel.updateTopicPushEnabled(false)
        viewModel.updateModerationPushEnabled(false)
        repository.completePendingUpdate(0, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()
        repository.completePendingUpdate(1, NotificationResult.Failure(NotificationFailure.Network))
        advanceUntilIdle()

        assertEquals(NotificationPushSettings(true, true), viewModel.uiState.value.pushSettings)
        assertFalse(viewModel.uiState.value.isTopicPushSaving)
        assertFalse(viewModel.uiState.value.isModerationPushSaving)
    }

    private fun createViewModel() = SettingsViewModel(
        authRepository = authRepository,
        userRepository = userRepository,
        versionName = "1.2.4",
    ).also { it.refreshSignature() }
}

private class FakeSettingsAuthRepository : AuthRepository {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.Guest)
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    var logoutCalled: Boolean = false
    var logoutError: Throwable? = null

    fun setAuthenticated() {
        mutableSessionState.value = UserSessionState.Authenticated("user-id")
    }

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
        rawNonce: String,
    ): SocialLoginResult = SocialLoginResult.LoginSuccess("user-id")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun logout() {
        logoutError?.let { throw it }
        logoutCalled = true
        mutableSessionState.value = UserSessionState.SignedOut
    }

    fun setSignedOut() {
        mutableSessionState.value = UserSessionState.SignedOut
    }
}

private class FakeSettingsUserRepository(private val onWithdraw: () -> Unit) : UserRepository {
    var profile: UserProfile = UserProfile(
        signatureUrl = "signature-url",
        signatureThumbnailUrl = "signature-thumbnail-url",
    )
    var profileError: Throwable? = null
    var profileAwait: CompletableDeferred<Unit>? = null
    var getMySignatureCalled: Int = 0
    var withdrawCalled: Boolean = false

    override suspend fun getMySignature(): UserProfile {
        getMySignatureCalled += 1
        profileAwait?.await()
        profileError?.let { throw it }
        return profile
    }

    override suspend fun updateMySignature(signaturePng: ByteArray): SignatureUpdateResult = error("Not used")

    override suspend fun withdraw() {
        withdrawCalled = true
        profileError = null
        onWithdraw()
    }
}

private class FakeSettingsNotificationRepository : NotificationRepository {
    var settings = NotificationPushSettings(true, true)
    var updateResult: NotificationResult<Unit> = NotificationResult.Success(Unit)
    var suspendUpdates: Boolean = false
    val updates = mutableListOf<NotificationPushSettingsUpdate>()
    val pendingUpdates = mutableListOf<PendingPushSettingsUpdate>()

    override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> =
        NotificationResult.Success(settings)

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> {
        updates += settings
        if (suspendUpdates) {
            val pending = PendingPushSettingsUpdate()
            pendingUpdates += pending
            return pending.result.await()
        }
        return updateResult
    }

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage> = error("Not used")

    override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
        error("Not used")

    override suspend fun getUnreadStatus(): NotificationResult<Boolean> = error("Not used")

    override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = error("Not used")

    override suspend fun markAllAsRead(): NotificationResult<Unit> = error("Not used")

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> = error("Not used")
}

private data class PendingPushSettingsUpdate(
    val result: CompletableDeferred<NotificationResult<Unit>> = CompletableDeferred(),
)

private fun FakeSettingsNotificationRepository.completePendingUpdate(
    index: Int,
    result: NotificationResult<Unit>,
) {
    pendingUpdates[index].result.complete(result)
}

private fun FakeSettingsNotificationRepository.completeOnlyPendingUpdate(result: NotificationResult<Unit>) {
    val pendingUpdate = pendingUpdates.single()
    pendingUpdate.result.complete(result)
}
