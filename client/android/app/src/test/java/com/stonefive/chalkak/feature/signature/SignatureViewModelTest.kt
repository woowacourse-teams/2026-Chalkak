package com.stonefive.chalkak.feature.signature

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.repository.SignatureRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignatureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSignatureRepository()
    private val encodedPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val encoder = RecordingSignaturePngEncoder(encodedPng)
    private val viewModel = SignatureViewModel(repository, encoder)

    @Test
    fun `사인을 그리기 전에는 제출할 수 없다`() {
        viewModel.onAction(SignatureUiAction.SubmitClicked)

        assertFalse(viewModel.uiState.value.canSubmit)
        assertEquals(0, encoder.encodeCount)
        assertEquals(0, repository.uploadCount)
    }

    @Test
    fun `사인을 그리면 제출 가능하고 되돌리면 다시 비활성화된다`() {
        viewModel.drawSampleStroke()

        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onAction(SignatureUiAction.UndoClicked)

        assertFalse(viewModel.uiState.value.hasSignature)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `전체 지우기는 모든 획을 제거한다`() {
        viewModel.drawSampleStroke()
        viewModel.drawSampleStroke()

        viewModel.onAction(SignatureUiAction.ClearClicked)

        assertTrue(
            viewModel.uiState.value.strokes
                .isEmpty(),
        )
    }

    @Test
    fun `제출 성공 시 PNG를 업로드하고 완료 이벤트를 전달한다`() = runTest {
        viewModel.drawSampleStroke()

        viewModel.onAction(SignatureUiAction.SubmitClicked)

        assertArrayEquals(encodedPng, repository.uploadedPng)
        assertEquals(1, encoder.encodeCount)
        val event = viewModel.uiEvent.first() as SignatureUiEvent.SignatureSaved
        assertArrayEquals(encodedPng, event.signaturePng)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `제출 실패 시 그린 사인을 유지하고 오류를 노출한다`() {
        val failure = IllegalStateException("upload failed")
        repository.failure = failure
        viewModel.drawSampleStroke()

        viewModel.onAction(SignatureUiAction.SubmitClicked)

        assertTrue(viewModel.uiState.value.hasSignature)
        assertSame(failure, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `제출 취소는 UI 오류로 노출하지 않는다`() {
        repository.failure = CancellationException("upload cancelled")
        viewModel.drawSampleStroke()

        viewModel.onAction(SignatureUiAction.SubmitClicked)

        assertTrue(viewModel.uiState.value.hasSignature)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    private fun SignatureViewModel.drawSampleStroke() {
        onAction(SignatureUiAction.StrokeStarted(SignaturePoint(0.2f, 0.3f)))
        onAction(SignatureUiAction.StrokeMoved(SignaturePoint(0.7f, 0.8f)))
        onAction(SignatureUiAction.StrokeFinished)
    }
}

private class RecordingSignaturePngEncoder(private val result: ByteArray) : SignaturePngEncoder {
    var encodeCount = 0

    override fun encode(strokes: List<SignatureStroke>): ByteArray {
        encodeCount += 1
        return result
    }
}

private class FakeSignatureRepository : SignatureRepository {
    var failure: Throwable? = null
    var uploadCount = 0
    var uploadedPng = ByteArray(0)

    override suspend fun uploadSignature(signaturePng: ByteArray) {
        uploadCount += 1
        uploadedPng = signaturePng
        failure?.let { throw it }
    }
}
