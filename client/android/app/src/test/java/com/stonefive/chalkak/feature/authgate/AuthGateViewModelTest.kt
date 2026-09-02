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
}

private class FakeAuthGateRepository : AuthRepository {
    val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit
}
