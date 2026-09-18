package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val feedbackRepository = FakeFeedbackRepository()

    @Test
    fun `내용이 비어 있으면 보내기를 할 수 없다`() = runTest {
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.updateContent("   ")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `내용이 있으면 보내기를 할 수 있다`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateContent("피드백 내용")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `제출에 성공하면 Submitted 이벤트를 보낸다`() = runTest {
        val viewModel = createViewModel()
        val events = mutableListOf<FeedbackUiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect(events::add) }

        viewModel.updateContent("좋은 서비스예요")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("좋은 서비스예요", feedbackRepository.submittedContent)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(listOf(FeedbackUiEvent.Submitted), events)

        collectJob.cancel()
    }

    @Test
    fun `제출 내용은 공백을 제거해서 전달한다`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateContent("  의견입니다  ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("의견입니다", feedbackRepository.submittedContent)
    }

    @Test
    fun `401 실패면 재인증 이벤트를 보낸다`() = runTest {
        feedbackRepository.error = FeedbackSubmissionException(FeedbackSubmissionFailure.UNAUTHORIZED)
        val viewModel = createViewModel()
        val events = mutableListOf<FeedbackUiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect(events::add) }

        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf(FeedbackUiEvent.ReauthenticationRequired), events)
        assertFalse(viewModel.uiState.value.isSubmitting)

        collectJob.cancel()
    }

    @Test
    fun `잘못된 내용 실패면 Toast 메시지를 보낸다`() = runTest {
        feedbackRepository.error = FeedbackSubmissionException(FeedbackSubmissionFailure.INVALID_CONTENT)
        val viewModel = createViewModel()

        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "피드백은 1자 이상 1000자 이하로 입력해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `네트워크 실패면 Toast 메시지를 보낸다`() = runTest {
        feedbackRepository.error = FeedbackSubmissionException(FeedbackSubmissionFailure.NETWORK)
        val viewModel = createViewModel()

        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "피드백을 보내지 못했어요. 다시 시도해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `Toast 메시지를 소비하면 상태에서 제거한다`() = runTest {
        feedbackRepository.error = FeedbackSubmissionException(FeedbackSubmissionFailure.NETWORK)
        val viewModel = createViewModel()

        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()

        val pendingMessage = viewModel.uiState.value.pendingMessage!!
        viewModel.onMessageShown(pendingMessage.id)

        assertNull(viewModel.uiState.value.pendingMessage)
    }

    private fun createViewModel() = FeedbackViewModel(
        feedbackRepository = feedbackRepository,
    )
}

private class FakeFeedbackRepository : FeedbackRepository {
    var submittedContent: String? = null
    var error: Throwable? = null

    override suspend fun submitFeedback(content: String) {
        submittedContent = content
        error?.let { throw it }
    }
}
