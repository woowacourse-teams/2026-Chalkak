package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeFeedbackDetailRepository()

    @Test
    fun `상세에서 404를 받으면 찾을 수 없음 상태가 된다`() = runTest {
        // Given  상세 요청이 404 / BUSINESS_ERROR
        repository.detailError = FeedbackInquiryException(FeedbackInquiryFailure.NOT_FOUND)

        // When   상세를 불러온다
        val viewModel = FeedbackDetailViewModel(feedbackId = "F", repository = repository)
        advanceUntilIdle()

        // Then   상태가 찾을 수 없음, 메시지 "문의를 찾을 수 없어요"
        assertEquals(FeedbackDetailStatus.NotFound("문의를 찾을 수 없어요"), viewModel.uiState.value.status)
    }

    @Test
    fun `삭제 204 빈 본문을 성공으로 처리한다`() = runTest {
        // Given  문의 B, 삭제 요청이 본문 없는 204
        val viewModel = FeedbackDetailViewModel(feedbackId = "B", repository = repository)
        advanceUntilIdle()

        // When   B를 삭제한다
        viewModel.delete()
        advanceUntilIdle()

        // Then   DELETE feedbacks/B, 상세 상태가 삭제됨
        assertEquals(listOf("B"), repository.deletedIds)
        assertEquals(FeedbackDetailStatus.Deleted, viewModel.uiState.value.status)
    }

    @Test
    fun `삭제에서 404를 받으면 찾을 수 없음 상태가 된다`() = runTest {
        // Given  삭제 요청이 404
        repository.deleteError = FeedbackInquiryException(FeedbackInquiryFailure.NOT_FOUND)
        val viewModel = FeedbackDetailViewModel(feedbackId = "B", repository = repository)
        advanceUntilIdle()

        // When   삭제한다
        viewModel.delete()
        advanceUntilIdle()

        // Then   상태가 찾을 수 없음, 메시지 "문의를 찾을 수 없어요"
        assertEquals(FeedbackDetailStatus.NotFound("문의를 찾을 수 없어요"), viewModel.uiState.value.status)
    }

    @Test
    fun `삭제 중 다시 눌러도 삭제 요청은 한 번이다`() = runTest {
        // Given  첫 삭제 요청이 아직 응답하지 않음
        val deleteGate = CompletableDeferred<Unit>()
        repository.deleteGate = deleteGate
        val viewModel = FeedbackDetailViewModel(feedbackId = "B", repository = repository)
        advanceUntilIdle()
        viewModel.delete()
        advanceUntilIdle()

        // When   삭제를 한 번 더 누른다
        viewModel.delete()
        advanceUntilIdle()

        // Then   삭제 요청 1회
        deleteGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("B"), repository.deletedIds)
    }
}

private class FakeFeedbackDetailRepository : FeedbackRepository {
    var detailError: Throwable? = null
    var deleteError: Throwable? = null
    var deleteGate: CompletableDeferred<Unit>? = null
    val deletedIds = mutableListOf<String>()

    override suspend fun submitFeedback(content: String) = Unit

    override suspend fun getFeedbacks(page: Int): FeedbackInquiryPage = error("사용하지 않음")

    override suspend fun getFeedbackDetail(feedbackId: String): FeedbackInquiryDetail {
        detailError?.let { throw it }
        return FeedbackInquiryDetail(
            id = feedbackId,
            content = "사진 업로드 후 화면이 멈춰요.",
            createdAt = "2026-11-03T06:00:00Z",
            answer = null,
        )
    }

    override suspend fun deleteFeedback(feedbackId: String) {
        deletedIds += feedbackId
        deleteGate?.await()
        deleteError?.let { throw it }
    }
}
