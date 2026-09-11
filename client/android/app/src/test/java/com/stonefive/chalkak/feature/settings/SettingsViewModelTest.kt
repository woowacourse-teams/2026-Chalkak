package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
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

    private fun createViewModel() = SettingsViewModel(
        authRepository = authRepository,
        userRepository = userRepository,
        versionName = "1.2.4",
    )
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
