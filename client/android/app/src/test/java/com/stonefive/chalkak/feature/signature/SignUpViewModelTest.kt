package com.stonefive.chalkak.feature.signature

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpFailure
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `회원가입 성공 시 완료 상태를 제공한다`() = runTest {
        val repository = FakeSignUpRepository()
        val viewModel = SignUpViewModel(repository)
        val signaturePng = byteArrayOf(1, 2, 3)

        viewModel.completeSignUp(signaturePng)

        assertEquals(SignUpStatus.Completed, viewModel.uiState.value.status)
        assertArrayEquals(signaturePng, repository.completedSignaturePng)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `회원가입 실패는 재시도 상태와 pending 메시지를 함께 제공한다`() = runTest {
        val repository = FakeSignUpRepository().apply {
            signUpResult = SocialSignUpResult.Failure(SocialSignUpFailure.NETWORK_UNAVAILABLE)
        }
        val viewModel = SignUpViewModel(repository)

        viewModel.completeSignUp(byteArrayOf(1))

        assertEquals(SignUpStatus.Idle, viewModel.uiState.value.status)
        assertEquals(
            "네트워크 연결을 확인해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }
}

private class FakeSignUpRepository : AuthRepository {
    override val sessionState: StateFlow<UserSessionState> =
        MutableStateFlow(UserSessionState.SignedOut)
    var completedSignaturePng = ByteArray(0)
    var signUpResult: SocialSignUpResult = SocialSignUpResult.Success("user-id")

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult {
        completedSignaturePng = signaturePng
        return signUpResult
    }

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit
}
