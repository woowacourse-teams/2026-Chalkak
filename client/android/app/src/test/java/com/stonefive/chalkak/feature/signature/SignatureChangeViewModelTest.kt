package com.stonefive.chalkak.feature.signature

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.SignatureUpdateFailure
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureChangeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = FakeSignatureChangeUserRepository()

    @Test
    fun `사인 변경 확인 시 저장하고 완료 상태를 제공한다`() = runTest {
        val viewModel = SignatureChangeViewModel(userRepository)

        viewModel.updateSignature(byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        assertEquals(
            SignatureChangeStatus.Completed(
                UserProfile(
                    signatureUrl = "updated-signature-url",
                    signatureThumbnailUrl = "updated-signature-url",
                ),
            ),
            viewModel.uiState.value.status,
        )
        assertTrue(userRepository.updatedPng.contentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `사인 변경 실패 시 Toast 메시지를 제공하고 재시도 상태로 돌아간다`() = runTest {
        userRepository.result = SignatureUpdateResult.Failure(
            SignatureUpdateFailure.NETWORK_UNAVAILABLE,
        )
        val viewModel = SignatureChangeViewModel(userRepository)
        val message = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiMessage.first() }

        viewModel.updateSignature(byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(
            UiMessage.Toast("네트워크 연결을 확인해 주세요."),
            message.await(),
        )
        assertEquals(SignatureChangeStatus.Idle, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}

private class FakeSignatureChangeUserRepository : UserRepository {
    var result: SignatureUpdateResult = SignatureUpdateResult.Success(
        UserProfile(
            signatureUrl = "updated-signature-url",
            signatureThumbnailUrl = "updated-signature-url",
        ),
    )
    var updatedPng: ByteArray = byteArrayOf()

    override suspend fun getMySignature(): UserProfile = error("Not used")

    override suspend fun updateMySignature(signaturePng: ByteArray): SignatureUpdateResult {
        updatedPng = signaturePng
        return result
    }

    override suspend fun withdraw() = Unit
}
