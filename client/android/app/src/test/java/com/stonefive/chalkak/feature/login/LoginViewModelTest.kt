package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.SocialAuthFailure
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoginRepository()
    private val viewModel = LoginViewModel(authRepository = repository)

    @Test
    fun `기존 회원 로그인 성공 시 인증 세션으로 전환한다`() = runTest {
        repository.loginResult = SocialLoginResult.LoginSuccess("user-id")

        viewModel.login(SocialLoginProvider.GOOGLE, "id-token")

        assertEquals(UserSessionState.Authenticated("user-id"), repository.sessionState.value)
        assertEquals("id-token", repository.idToken)
        assertEquals(LoginStatus.Authenticated, viewModel.uiState.value.status)
    }

    @Test
    fun `신규 회원이면 회원가입 필요 상태를 제공한다`() = runTest {
        repository.loginResult = SocialLoginResult.SignUpRequired

        viewModel.login(SocialLoginProvider.GOOGLE, "id-token")

        assertEquals(LoginStatus.SignUpRequired, viewModel.uiState.value.status)

        viewModel.signUpRequiredHandled()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `Google 자격 증명 요청이 취소되면 재시도 가능한 상태로 돌아간다`() {
        assertTrue(viewModel.startCredentialRequest(SocialLoginProvider.GOOGLE))
        assertEquals(LoginStatus.Loading, viewModel.uiState.value.status)

        viewModel.credentialRequestCancelled()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `Kakao 자격 증명 요청이 취소되면 재시도 가능한 상태로 돌아간다`() {
        assertTrue(viewModel.startCredentialRequest(SocialLoginProvider.KAKAO))
        assertEquals(LoginStatus.Loading, viewModel.uiState.value.status)
        assertEquals(SocialLoginProvider.KAKAO, viewModel.uiState.value.activeProvider)

        viewModel.credentialRequestCancelled()

        assertEquals(LoginStatus.Idle, viewModel.uiState.value.status)
        assertEquals(null, viewModel.uiState.value.activeProvider)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `Kakao 로그인은 provider와 idToken을 repository로 전달한다`() = runTest {
        repository.loginResult = SocialLoginResult.LoginSuccess("user-id")

        viewModel.login(SocialLoginProvider.KAKAO, "kakao-id-token")

        assertEquals(SocialLoginProvider.KAKAO, repository.provider)
        assertEquals("kakao-id-token", repository.idToken)
        assertEquals(LoginStatus.Authenticated, viewModel.uiState.value.status)
    }

    @Test
    fun `백엔드 인증 실패를 사용자 메시지로 변환한다`() {
        repository.loginResult = SocialLoginResult.Failure(SocialAuthFailure.UNAUTHORIZED)

        viewModel.login(SocialLoginProvider.GOOGLE, "id-token")

        assertEquals(
            "Google 계정을 확인할 수 없어요. 다시 시도해 주세요.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `Kakao 백엔드 인증 실패를 카카오 사용자 메시지로 변환한다`() {
        repository.loginResult = SocialLoginResult.Failure(SocialAuthFailure.UNAUTHORIZED)

        viewModel.login(SocialLoginProvider.KAKAO, "id-token")

        assertEquals(
            "카카오 계정을 확인할 수 없어요. 다시 시도해 주세요.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `비회원으로 계속하면 게스트 세션으로 전환한다`() = runTest {
        viewModel.continueAsGuest()

        assertEquals(UserSessionState.Guest, repository.sessionState.value)
        assertEquals(LoginStatus.GuestAccessGranted, viewModel.uiState.value.status)
    }
}

private class FakeLoginRepository : AuthRepository {
    var loginResult: SocialLoginResult = SocialLoginResult.SignUpRequired
    var provider: SocialLoginProvider? = null
    var idToken: String? = null

    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult {
        this.provider = provider
        this.idToken = idToken
        if (loginResult is SocialLoginResult.LoginSuccess) {
            mutableSessionState.value = UserSessionState.Authenticated(
                (loginResult as SocialLoginResult.LoginSuccess).userId,
            )
        }
        return loginResult
    }

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun logout() = Unit

    override suspend fun withdraw() = Unit
}
