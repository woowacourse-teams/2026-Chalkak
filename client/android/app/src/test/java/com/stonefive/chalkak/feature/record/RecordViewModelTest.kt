package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
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
    fun `인증되지 않은 기록 조회는 로그인 필요 상태로 표시한다`() = runTest {
        advanceUntilIdle()
        val unauthorizedMonth = RecordTestMonth.minusMonths(1)
        repository.unauthorizedMonth = unauthorizedMonth

        viewModel.moveToPreviousMonth()
        advanceUntilIdle()

        assertEquals("로그인이 필요해요", viewModel.uiState.value.errorMessage)
        assertEquals(true, viewModel.uiState.value.isLoginRequired)
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

    @Test
    fun `삭제 완료된 게시물을 기록에서 제거하고 다음 게시물을 선택한다`() = runTest {
        advanceUntilIdle()

        viewModel.removeDeletedPost("post-2")

        assertEquals(
            listOf("post-5"),
            viewModel.uiState.value.posts
                .map { it.postId },
        )
        assertEquals(RecordTestMonth.atDay(5), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `달력 이미지 저장 결과를 Toast 메시지로 제공하고 표시 후 제거한다`() = runTest {
        viewModel.onCalendarImageSaved(saved = true)

        val message = viewModel.uiState.value.pendingMessage as UiMessage.Toast
        assertEquals("달력을 이미지로 저장했어요", message.text)

        viewModel.onMessageShown(message.id)

        assertEquals(null, viewModel.uiState.value.pendingMessage)
    }

    @Test
    fun `이미지 저장 권한이 없으면 Toast 메시지를 제공한다`() = runTest {
        viewModel.onStoragePermissionDenied()

        assertEquals(
            "이미지 저장 권한이 필요해요",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }
}

private val RecordTestMonth = YearMonth.of(2026, 8)
private val RecordLatestMonth = YearMonth.of(2026, 9)

private class FakePostRepository : PostRepository {
    val requests = mutableListOf<YearMonth>()
    var failureMonth: YearMonth? = null
    var unauthorizedMonth: YearMonth? = null

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> {
        requests += month
        if (month == unauthorizedMonth) return HomeResult.Failure(HomeFailure.Unauthorized)
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

    override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

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
