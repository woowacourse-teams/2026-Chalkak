package com.stonefive.chalkak.feature.authgate

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `로그아웃과 게스트와 회원 상태를 서로 구분해 앱 진입 상태를 만든다`() = runTest {
        val repository = FakeAuthGateRepository()
        val viewModel = AuthGateViewModel(repository)

        advanceUntilIdle()
        assertEquals(AuthGateUiState.LoginRequired, viewModel.uiState.value)

        repository.mutableSessionState.value = UserSessionState.Guest
        advanceUntilIdle()
        assertEquals(AuthGateUiState.AppAccessible, viewModel.uiState.value)

        repository.mutableSessionState.value = UserSessionState.Authenticated("user-id")
        advanceUntilIdle()
        assertEquals(AuthGateUiState.Authenticated, viewModel.uiState.value)
    }

    @Test
    fun `재인증 필요 상태에서는 앱 화면을 새로 만들지 않는다`() = runTest {
        // Given 게이트 상태가 로그인된 앱 화면
        // When  세션이 ReauthenticationRequired("u1")
        // Then  게이트 상태는 앱 화면 유지 + 재인증 필요("u1"), LoginRequired 아님
        val repository = FakeAuthGateRepository()
        repository.mutableSessionState.value = UserSessionState.Authenticated("u1")
        val viewModel = AuthGateViewModel(repository)
        advanceUntilIdle()
        assertEquals(AuthGateUiState.Authenticated, viewModel.uiState.value)

        repository.mutableSessionState.value = UserSessionState.ReauthenticationRequired("u1")
        advanceUntilIdle()

        assertEquals(AuthGateUiState.ReauthenticationRequired("u1"), viewModel.uiState.value)
    }

    @Test
    fun `재인증 로그인 화면에서 게스트로 둘러보기를 누르면 이전 화면을 버리고 게스트 홈으로 간다`() = runTest {
        // Given 게이트 상태가 앱 화면 유지 + 재인증 필요("u1")
        // When  세션이 Guest
        // Then  게이트 상태 AppAccessible(게스트 앱 화면을 새로 만듦), 재인증 필요 없음
        val repository = FakeAuthGateRepository()
        repository.mutableSessionState.value = UserSessionState.ReauthenticationRequired("u1")
        val viewModel = AuthGateViewModel(repository)
        advanceUntilIdle()

        repository.mutableSessionState.value = UserSessionState.Guest
        advanceUntilIdle()

        assertEquals(AuthGateUiState.AppAccessible, viewModel.uiState.value)
    }
}

private class FakeAuthGateRepository : AuthRepository {
    val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
        rawNonce: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit
}
