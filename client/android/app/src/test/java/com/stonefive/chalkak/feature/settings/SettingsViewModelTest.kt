package com.stonefive.chalkak.feature.settings

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsAuthRepository()

    @Test
    fun `프로필이 있으면 로그인 상태와 서명을 제공한다`() = runTest {
        repository.profile = UserProfile(signatureUrl = "signature-url")

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals("signature-url", viewModel.uiState.value.signatureModel)
        assertEquals("1.2.4", viewModel.uiState.value.versionName)
    }

    @Test
    fun `프로필이 없으면 비회원 상태를 제공한다`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun `로그아웃 API 성공 후 로그인 화면으로 이동한다`() = runTest {
        repository.profile = UserProfile(signatureUrl = null)
        val viewModel = createViewModel()

        viewModel.showLogoutDialog()

        assertFalse(repository.logoutCalled)

        viewModel.confirmAccountAction()

        assertTrue(repository.logoutCalled)
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(UserSessionState.SignedOut, repository.sessionState.value)
    }

    @Test
    fun `계정 작업 다이얼로그를 취소하면 API를 호출하지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.showLogoutDialog()
        viewModel.dismissAccountDialog()

        assertEquals(null, viewModel.uiState.value.accountDialog)
        assertFalse(repository.logoutCalled)
    }

    @Test
    fun `회원탈퇴 확인 시 회원탈퇴 API를 호출한다`() = runTest {
        repository.profile = UserProfile(signatureUrl = null)
        val viewModel = createViewModel()

        viewModel.showWithdrawDialog()
        viewModel.confirmAccountAction()

        assertTrue(repository.withdrawCalled)
        assertEquals(UserSessionState.SignedOut, repository.sessionState.value)
    }

    private fun createViewModel() = SettingsViewModel(
        authRepository = repository,
        versionName = "1.2.4",
    )
}

private class FakeSettingsAuthRepository : AuthRepository {
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.Guest)
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    var profile: UserProfile? = null
        set(value) {
            field = value
            mutableSessionState.value = if (value == null) {
                UserSessionState.Guest
            } else {
                UserSessionState.Authenticated("user-id")
            }
        }
    var logoutCalled: Boolean = false
    var withdrawCalled: Boolean = false

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = SocialLoginResult.LoginSuccess("user-id")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun getMyProfile(): UserProfile? = profile

    override suspend fun logout() {
        logoutCalled = true
        profile = null
        mutableSessionState.value = UserSessionState.SignedOut
    }

    override suspend fun withdraw() {
        withdrawCalled = true
        profile = null
        mutableSessionState.value = UserSessionState.SignedOut
    }
}
