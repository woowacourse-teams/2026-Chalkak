package com.stonefive.chalkak.feature.feedback

import androidx.lifecycle.SavedStateHandle
import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
            "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요.",
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

    @Test
    fun `저장된 본문을 복원하되 자동 제출하지 않는다`() = runTest {
        val handle = SavedStateHandle(mapOf("feedback_content" to "  작성 중인 본문  "))

        val restored = FeedbackViewModel(feedbackRepository, handle)
        advanceUntilIdle()

        assertEquals("  작성 중인 본문  ", restored.uiState.value.content)
        assertFalse(restored.uiState.value.isSubmitting)
        assertNull(feedbackRepository.submittedContent)
    }

    @Test
    fun `변경한 본문을 저장하고 새 ViewModel에서 복원한다`() {
        val handle = SavedStateHandle()
        val original = FeedbackViewModel(feedbackRepository, handle)
        original.updateContent("작성 중인 피드백")

        val restoredHandle = SavedStateHandle(mapOf("feedback_content" to handle.get<String>("feedback_content")))
        val restored = FeedbackViewModel(feedbackRepository, restoredHandle)

        assertEquals("작성 중인 피드백", restored.uiState.value.content)
        original.updateContent("")
        assertEquals("", handle.get<String>("feedback_content"))
    }

    @Test
    fun `제출 중 보내기를 다시 눌러도 제출은 한 번이다`() = runTest {
        // Given  첫 제출이 아직 응답하지 않음
        val submitGate = CompletableDeferred<Unit>()
        feedbackRepository.submitGate = submitGate
        val viewModel = createViewModel()
        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()

        // When   보내기를 한 번 더 누른다
        viewModel.submit()
        advanceUntilIdle()

        // Then   제출 요청 1회
        submitGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, feedbackRepository.submitCount)
    }

    @Test
    fun `제출이 네트워크 오류면 다시 보내지 않고 목록 확인을 안내한다`() = runTest {
        // Given  제출이 네트워크 오류(타임아웃 포함)
        feedbackRepository.error = FeedbackSubmissionException(FeedbackSubmissionFailure.NETWORK)
        val viewModel = createViewModel()
        viewModel.updateContent("피드백")

        // When   보낸다
        viewModel.submit()
        advanceUntilIdle()

        // Then   제출 요청 1회, 토스트 "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요."
        assertEquals(1, feedbackRepository.submitCount)
        assertEquals(
            "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `접수 제한이면 다음 접수 가능 시각을 짧게 안내한다`() = runTest {
        // Given  지금 2026-11-06T07:00:00Z(서울 16:00), 400 / BUSINESS_ERROR,
        //        message="문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. 다음 접수 가능 시각(UTC): 2026-11-06T08:30:22Z"
        val now = Instant.parse("2026-11-06T07:00:00Z")
        val limitMessage = "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. 다음 접수 가능 시각(UTC): "
        val noTimeMessage = "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다."

        // When   보낸다
        val limited = submitWithInvalidContent(now, limitMessage + "2026-11-06T08:30:22Z")
        val onTheMinute = submitWithInvalidContent(now, limitMessage + "2026-11-06T08:30:00Z")
        val fractionalSecond = submitWithInvalidContent(now, limitMessage + "2026-11-06T08:30:00.5Z")
        val tomorrow = submitWithInvalidContent(now, limitMessage + "2026-11-06T15:10:00Z")
        val later = submitWithInvalidContent(now, limitMessage + "2026-11-07T15:10:00Z")
        val withoutTime = submitWithInvalidContent(now, noTimeMessage)
        val withoutMessage = submitWithInvalidContent(now, null)

        // Then   토스트 "지금은 문의를 보낼 수 없어요.\n오늘 17:31부터 다시 보낼 수 있어요."
        //        (초·나노초가 있으면 분을 올림, 서울 기준 다음 날이면 "내일 HH:mm", 그 밖은 "M월 d일 HH:mm")
        //        message에 UTC 시각이 없으면 그 문구 그대로, message가 없으면 기존 "피드백은 1자 이상 1000자 이하로 입력해 주세요."
        assertEquals("지금은 문의를 보낼 수 없어요.\n오늘 17:31부터 다시 보낼 수 있어요.", limited)
        assertEquals("지금은 문의를 보낼 수 없어요.\n오늘 17:30부터 다시 보낼 수 있어요.", onTheMinute)
        assertEquals("지금은 문의를 보낼 수 없어요.\n오늘 17:31부터 다시 보낼 수 있어요.", fractionalSecond)
        assertEquals("지금은 문의를 보낼 수 없어요.\n내일 00:10부터 다시 보낼 수 있어요.", tomorrow)
        assertEquals("지금은 문의를 보낼 수 없어요.\n11월 8일 00:10부터 다시 보낼 수 있어요.", later)
        assertEquals(noTimeMessage, withoutTime)
        assertEquals("피드백은 1자 이상 1000자 이하로 입력해 주세요.", withoutMessage)
    }

    private fun TestScope.submitWithInvalidContent(
        now: Instant,
        serverMessage: String?,
    ): String {
        val repository = FakeFeedbackRepository().apply {
            error = FeedbackSubmissionException(
                reason = FeedbackSubmissionFailure.INVALID_CONTENT,
                serverMessage = serverMessage,
            )
        }
        val viewModel = FeedbackViewModel(
            feedbackRepository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        viewModel.updateContent("피드백")
        viewModel.submit()
        advanceUntilIdle()
        return (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text
    }

    @Test
    fun `글자 수는 code point로 세어 이모지 1,000자까지 보낼 수 있다`() = runTest {
        // Given  "😀" 1,000개(UTF-16 2,000자) / 1,001개
        val thousandEmoji = "😀".repeat(1_000)
        val overLimitEmoji = "😀".repeat(1_001)
        val viewModel = createViewModel()
        val overLimitViewModel = createViewModel()

        // When   보내기 가능 여부를 묻고 제출한다
        viewModel.updateContent(thousandEmoji)
        overLimitViewModel.updateContent(overLimitEmoji)
        val canSubmitThousand = viewModel.uiState.value.canSubmit
        val canSubmitOverLimit = overLimitViewModel.uiState.value.canSubmit
        viewModel.submit()
        advanceUntilIdle()

        // Then   1,000개는 보내기 가능·제출 요청됨, 1,001개는 보내기 불가
        assertTrue(canSubmitThousand)
        assertEquals(thousandEmoji, feedbackRepository.submittedContent)
        assertFalse(canSubmitOverLimit)
    }

    private fun createViewModel() = FeedbackViewModel(
        feedbackRepository = feedbackRepository,
    )
}

private class FakeFeedbackRepository : FeedbackRepository {
    var submittedContent: String? = null
    var error: Throwable? = null
    var submitCount = 0
    var submitGate: CompletableDeferred<Unit>? = null

    override suspend fun submitFeedback(content: String) {
        submitCount++
        submittedContent = content
        submitGate?.await()
        error?.let { throw it }
    }

    override suspend fun getFeedbacks(page: Int): FeedbackInquiryPage = error("사용하지 않음")

    override suspend fun getFeedbackDetail(feedbackId: String): FeedbackInquiryDetail = error("사용하지 않음")

    override suspend fun deleteFeedback(feedbackId: String) = Unit
}
