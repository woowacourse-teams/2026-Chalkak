package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.FeedbackInquiry
import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeFeedbackListRepository()

    @Test
    fun `첫 페이지가 빈 목록이면 빈 화면 상태가 된다`() = runTest {
        // Given  feedbacks: [], hasNext=false
        repository.pages[1] = page(currentPage = 1, hasNext = false)

        // When   목록을 불러온다
        val viewModel = FeedbackListViewModel(repository)
        advanceUntilIdle()

        // Then   목록 상태가 빈 목록(Empty)
        assertEquals(FeedbackListStatus.Empty, viewModel.uiState.value.status)
    }

    @Test
    fun `다음 페이지가 있으면 이어 붙이고 겹친 문의는 한 번만 둔다`() = runTest {
        // Given  1페이지 A, B (hasNext=true), 2페이지 B, C
        repository.pages[1] = page(currentPage = 1, hasNext = true, "A", "B")
        repository.pages[2] = page(currentPage = 2, hasNext = false, "B", "C")
        val viewModel = FeedbackListViewModel(repository)
        advanceUntilIdle()

        // When   다음 페이지를 불러온다
        viewModel.loadNextPage()
        advanceUntilIdle()

        // Then   page=2 요청, 목록이 A, B, C
        assertEquals(listOf(1, 2), repository.requestedPages)
        assertEquals(
            listOf("A", "B", "C"),
            viewModel.uiState.value.items
                .map { it.id },
        )
    }

    @Test
    fun `첫 페이지 조회가 실패하면 실패 상태가 된다`() = runTest {
        // Given  목록 요청이 500
        repository.errors[1] = FeedbackInquiryException(FeedbackInquiryFailure.UNKNOWN)

        // When   목록을 불러온다
        val viewModel = FeedbackListViewModel(repository)
        advanceUntilIdle()

        // Then   목록 상태가 실패(Failed), 항목 없음
        assertEquals(FeedbackListStatus.Failed, viewModel.uiState.value.status)
        assertTrue(
            viewModel.uiState.value.items
                .isEmpty(),
        )
    }

    @Test
    fun `목록 새로고침은 첫 페이지부터 다시 불러와 목록을 바꾼다`() = runTest {
        // Given  2페이지까지 불러온 A, B, C
        repository.pages[1] = page(currentPage = 1, hasNext = true, "A", "B")
        repository.pages[2] = page(currentPage = 2, hasNext = false, "C")
        val viewModel = FeedbackListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()
        assertEquals(
            listOf("A", "B", "C"),
            viewModel.uiState.value.items
                .map { it.id },
        )
        repository.pages[1] = page(currentPage = 1, hasNext = false, "D", "A")
        repository.requestedPages.clear()

        // When   새로고침한다(접수 성공·삭제 성공·당겨 새로고침에서 부름)
        viewModel.refresh()
        advanceUntilIdle()

        // Then   page=1 요청, 목록이 새 응답 D, A
        assertEquals(listOf(1), repository.requestedPages)
        assertEquals(
            listOf("D", "A"),
            viewModel.uiState.value.items
                .map { it.id },
        )
    }

    @Test
    fun `목록 항목의 답변 여부를 답변 완료·답변 대기로 담는다`() = runTest {
        // Given  항목 A hasAnswer=true, B hasAnswer=false, C 필드 없음
        repository.pages[1] = FeedbackInquiryPage(
            currentPage = 1,
            hasNext = false,
            inquiries = listOf(
                FeedbackInquiry(id = "A", content = "문의 A", createdAt = "2026-11-03T06:00:00Z", hasAnswer = true),
                FeedbackInquiry(id = "B", content = "문의 B", createdAt = "2026-11-03T06:00:00Z", hasAnswer = false),
                FeedbackInquiry(id = "C", content = "문의 C", createdAt = "2026-11-03T06:00:00Z", hasAnswer = null),
            ),
        )

        // When   목록을 불러온다
        val viewModel = FeedbackListViewModel(repository)
        advanceUntilIdle()

        // Then   A 답변 완료, B 답변 대기, C 상태 없음(null)
        assertEquals(
            listOf(FeedbackAnswerStatus.Answered, FeedbackAnswerStatus.Waiting, null),
            viewModel.uiState.value.items
                .map { it.answerStatus },
        )
    }

    private fun page(
        currentPage: Int,
        hasNext: Boolean,
        vararg ids: String,
    ) = FeedbackInquiryPage(
        currentPage = currentPage,
        hasNext = hasNext,
        inquiries = ids.map { id ->
            FeedbackInquiry(id = id, content = "문의 $id", createdAt = "2026-11-03T06:00:00Z")
        },
    )
}

private class FakeFeedbackListRepository : FeedbackRepository {
    val pages = mutableMapOf<Int, FeedbackInquiryPage>()
    val errors = mutableMapOf<Int, Throwable>()
    val requestedPages = mutableListOf<Int>()

    override suspend fun submitFeedback(content: String) = Unit

    override suspend fun getFeedbacks(page: Int): FeedbackInquiryPage {
        requestedPages += page
        errors[page]?.let { throw it }
        return pages[page] ?: FeedbackInquiryPage(currentPage = page, hasNext = false, inquiries = emptyList())
    }

    override suspend fun getFeedbackDetail(feedbackId: String): FeedbackInquiryDetail = error("사용하지 않음")

    override suspend fun deleteFeedback(feedbackId: String) = Unit
}
