package com.stonefive.chalkak.feature.signature

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
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
}

private class FakeSignUpRepository : AuthRepository {
    override val sessionState: StateFlow<UserSessionState> =
        MutableStateFlow(UserSessionState.SignedOut)
    var completedSignaturePng = ByteArray(0)

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult {
        completedSignaturePng = signaturePng
        return SocialSignUpResult.Success("user-id")
    }

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit

    override suspend fun withdraw() = Unit
}
