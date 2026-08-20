package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoginRepository()
    private val viewModel = LoginViewModel(authRepository = repository)

    @Test
    fun `소셜 로그인에 성공하면 홈 이동 이벤트를 전달한다`() = runTest {
        viewModel.login(SocialLoginProvider.KAKAO)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(LoginUiEvent.NavigateToHome, viewModel.uiEvent.first())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `비회원으로 계속하면 홈 이동 이벤트를 전달한다`() = runTest {
        viewModel.continueAsGuest()

        assertEquals(LoginUiEvent.NavigateToHome, viewModel.uiEvent.first())
    }

    @Test
    fun `로그인에 실패하면 오류를 상태로 제공한다`() {
        repository.failure = IllegalStateException("로그인 실패")

        viewModel.login(SocialLoginProvider.GOOGLE)

        assertEquals(repository.failure, viewModel.uiState.value.error)
    }
}

private class FakeLoginRepository : AuthRepository {
    var failure: Throwable? = null

    override suspend fun login(provider: SocialLoginProvider): AuthSession.Authenticated {
        failure?.let { throw it }
        return AuthSession.Authenticated(provider)
    }

    override suspend fun continueAsGuest(): AuthSession.Guest {
        failure?.let { throw it }
        return AuthSession.Guest
    }

    override suspend fun getMyProfile(): UserProfile? = null

    override suspend fun logout() = Unit

    override suspend fun withdraw() = Unit
}
