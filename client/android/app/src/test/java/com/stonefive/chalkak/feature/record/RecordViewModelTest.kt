package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakePostRepository
    private lateinit var viewModel: RecordViewModel

    @Before
    fun setUp() {
        repository = FakePostRepository()
        viewModel = RecordViewModel(
            repository = repository,
            initialMonth = RecordTestMonth,
            latestMonth = RecordLatestMonth,
        )
    }

    @Test
    fun loadsInitialMonth() = runTest {
        advanceUntilIdle()

        assertEquals(RecordTestMonth, viewModel.uiState.value.month)
        assertEquals(
            2,
            viewModel.uiState.value.selectedDate
                ?.dayOfMonth,
        )
        assertEquals(2, viewModel.uiState.value.posts.size)
        assertEquals(listOf(RecordTestMonth), repository.requests)
    }

    @Test
    fun selectsPostDate() = runTest {
        advanceUntilIdle()

        val selectedDate = RecordTestMonth.atDay(5)
        viewModel.selectDate(selectedDate)

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun keepsSelectionForDateWithoutPost() = runTest {
        advanceUntilIdle()

        viewModel.selectDate(RecordTestMonth.atDay(6))

        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun movesToPreviousMonthEvenWhenItHasNoPosts() = runTest {
        advanceUntilIdle()

        viewModel.moveToPreviousMonth()
        advanceUntilIdle()

        assertEquals(RecordTestMonth.minusMonths(1), viewModel.uiState.value.month)
        assertEquals(emptyList<PostCalendarItem>(), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun failedMonthLoadClearsPreviousMonthContent() = runTest {
        advanceUntilIdle()
        val failedMonth = RecordTestMonth.minusMonths(1)
        repository.failureMonth = failedMonth

        viewModel.moveToPreviousMonth()
        advanceUntilIdle()

        assertEquals(failedMonth, viewModel.uiState.value.month)
        assertEquals(emptyList<PostCalendarItem>(), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals("조회할 수 없는 연월이에요", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `실패한 현재 월을 재시도해 복구한다`() = runTest {
        advanceUntilIdle()
        val failedMonth = RecordTestMonth.minusMonths(1)
        repository.failureMonth = failedMonth
        viewModel.moveToPreviousMonth()
        advanceUntilIdle()
        repository.failureMonth = null

        viewModel.retryCurrentMonth()
        advanceUntilIdle()

        assertEquals(listOf(RecordTestMonth, failedMonth, failedMonth), repository.requests)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals(failedMonth, viewModel.uiState.value.month)
    }

    @Test
    fun movesToNextMonthOnlyUpToLatestMonth() = runTest {
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.canGoNext)
        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, viewModel.uiState.value.month)
        assertEquals(false, viewModel.uiState.value.canGoNext)

        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(listOf(RecordTestMonth, RecordLatestMonth), repository.requests)
    }
}

private val RecordTestMonth = YearMonth.of(2026, 8)
private val RecordLatestMonth = YearMonth.of(2026, 9)

private class FakePostRepository : PostRepository {
    val requests = mutableListOf<YearMonth>()
    var failureMonth: YearMonth? = null

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> {
        requests += month
        if (month == failureMonth) return HomeResult.Failure(HomeFailure.Http(400))

        return HomeResult.Success(
            PostCalendar(
                month = month,
                posts = if (month == RecordTestMonth) {
                    listOf(
                        calendarPost(month, day = 2),
                        calendarPost(month, day = 5),
                    )
                } else {
                    emptyList()
                },
            ),
        )
    }

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> = error("unused")

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = error("unused")

    private fun calendarPost(
        month: YearMonth,
        day: Int,
    ) = PostCalendarItem(
        postId = "post-$day",
        topicDate = month.atDay(day),
        thumbnailImageUrl = "photo-$day",
        status = PostStatus.APPROVED,
    )
}
