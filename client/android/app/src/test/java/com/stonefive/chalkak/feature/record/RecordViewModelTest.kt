package com.stonefive.chalkak.feature.record

import androidx.lifecycle.SavedStateHandle
import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.AuthorPostsPage
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
            currentMonthProvider = { RecordLatestMonth },
        )
    }

    @Test
    fun `재조회 실패로 이전 화면을 복구해도 새로 선택한 날짜와 저장값을 유지한다`() = runTest {
        for (throws in listOf(false, true)) {
            val handle = SavedStateHandle()
            val restoredRepository = FakePostRepository()
            val original = RecordViewModel(
                restoredRepository,
                initialMonth = RecordTestMonth,
                currentMonthProvider = { RecordLatestMonth },
                savedStateHandle = handle,
            )
            advanceUntilIdle()
            val gate = CompletableDeferred<HomeResult<PostCalendar>>()
            restoredRepository.pendingCalendarResult = gate
            original.revalidate()
            advanceUntilIdle()
            original.selectDate(RecordTestMonth.atDay(2))

            if (throws) {
                gate.completeExceptionally(IllegalStateException("network"))
            } else {
                gate.complete(HomeResult.Failure(HomeFailure.Network))
            }
            advanceUntilIdle()

            assertEquals(RecordTestMonth.atDay(2), original.uiState.value.selectedDate)
            assertEquals(RecordTestMonth.atDay(2).toString(), handle.get<String>("record_selected_date"))
        }
    }

    @Test
    fun `재조회 중 선택한 날짜는 응답 후에도 유지한다`() = runTest {
        advanceUntilIdle()
        val response = repository.getPostCalendar(RecordTestMonth)
        val gate = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = gate
        viewModel.revalidate()
        advanceUntilIdle()

        viewModel.selectDate(RecordTestMonth.atDay(2))
        gate.complete(response)
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `복원한 선택 날짜가 게시물에 있으면 최신 날짜보다 우선한다`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "record_month" to RecordTestMonth.toString(),
                "record_selected_date" to RecordTestMonth.atDay(2).toString(),
            ),
        )
        val restored = RecordViewModel(
            FakePostRepository(),
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), restored.uiState.value.selectedDate)
    }

    @Test
    fun `선택 날짜를 저장하고 새 ViewModel에서 복원한다`() = runTest {
        val handle = SavedStateHandle()
        val original = RecordViewModel(
            FakePostRepository(),
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        original.selectDate(RecordTestMonth.atDay(2))

        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = RecordViewModel(
            FakePostRepository(),
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = SavedStateHandle(snapshot),
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2).toString(), handle.get<String>("record_selected_date"))
        assertEquals(RecordTestMonth.atDay(2), restored.uiState.value.selectedDate)
    }

    @Test
    fun `복원 날짜가 없거나 다른 달이거나 잘못된 값이면 기존 날짜를 선택한다`() = runTest {
        for (savedDate in listOf(
            "invalid",
            RecordTestMonth.atDay(3).toString(),
            RecordLatestMonth.atDay(1).toString(),
        )) {
            val handle = SavedStateHandle(mapOf("record_selected_date" to savedDate))
            val restored = RecordViewModel(
                FakePostRepository(),
                initialMonth = RecordTestMonth,
                currentMonthProvider = { RecordLatestMonth },
                savedStateHandle = handle,
            )
            advanceUntilIdle()

            assertEquals(RecordTestMonth.atDay(5), restored.uiState.value.selectedDate)
            assertEquals(RecordTestMonth.atDay(5).toString(), handle.get<String>("record_selected_date"))
        }
    }

    @Test
    fun `조회 중과 실패 후 재시도에도 복원 날짜를 잃지 않는다`() = runTest {
        val gate = CompletableDeferred<HomeResult<PostCalendar>>()
        val restoredRepository = FakePostRepository().apply { pendingCalendarResult = gate }
        val savedDate = RecordTestMonth.atDay(2).toString()
        val handle = SavedStateHandle(mapOf("record_selected_date" to savedDate))
        val restored = RecordViewModel(
            restoredRepository,
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        assertEquals(savedDate, handle.get<String>("record_selected_date"))

        gate.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
        assertEquals(savedDate, handle.get<String>("record_selected_date"))
        restoredRepository.pendingCalendarResult = null
        restored.retryCurrentMonth()
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), restored.uiState.value.selectedDate)
    }

    @Test
    fun `빈 달 응답은 저장된 선택 날짜를 제거한다`() = runTest {
        val emptyMonth = RecordTestMonth.minusMonths(1)
        val handle = SavedStateHandle(mapOf("record_selected_date" to emptyMonth.atDay(2).toString()))
        val restored = RecordViewModel(
            FakePostRepository(),
            initialMonth = emptyMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        assertEquals(null, restored.uiState.value.selectedDate)
        assertEquals(null, handle.get<String>("record_selected_date"))
    }

    @Test
    fun `선택한 게시물을 삭제하면 저장 날짜도 다음 게시물로 바뀐다`() = runTest {
        val handle = SavedStateHandle()
        val original = RecordViewModel(
            FakePostRepository(),
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        original.selectDate(RecordTestMonth.atDay(2))
        original.removeDeletedPost("post-2")

        assertEquals(RecordTestMonth.atDay(5).toString(), handle.get<String>("record_selected_date"))
    }

    @Test
    fun `저장된 월을 초기 월보다 우선해서 조회한다`() = runTest {
        val restoredRepository = FakePostRepository()
        val handle = SavedStateHandle(mapOf("record_month" to RecordTestMonth.toString()))
        val restored = RecordViewModel(
            restoredRepository,
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth, restored.uiState.value.month)
        assertEquals(listOf(RecordTestMonth), restoredRepository.requests)
    }

    @Test
    fun `월 이동을 저장하고 새 ViewModel에서 복원한다`() = runTest {
        val handle = SavedStateHandle()
        val original = RecordViewModel(
            FakePostRepository(),
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        original.moveToPreviousMonth()
        advanceUntilIdle()

        val restoredRepository = FakePostRepository()
        val restored = RecordViewModel(
            restoredRepository,
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = SavedStateHandle(mapOf("record_month" to handle.get<String>("record_month"))),
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth.toString(), handle.get<String>("record_month"))
        assertEquals(RecordTestMonth, restored.uiState.value.month)
        assertEquals(listOf(RecordTestMonth), restoredRepository.requests)
    }

    @Test
    fun `잘못된 저장 월은 기존 초기 월로 조회한다`() = runTest {
        val restoredRepository = FakePostRepository()
        val restored = RecordViewModel(
            restoredRepository,
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = SavedStateHandle(mapOf("record_month" to "invalid")),
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth, restored.uiState.value.month)
        assertEquals(listOf(RecordTestMonth), restoredRepository.requests)
    }

    @Test
    fun `월 조회가 실패해도 선택한 월은 저장한다`() = runTest {
        val restoredRepository = FakePostRepository().apply { failureMonth = RecordTestMonth }
        val handle = SavedStateHandle()
        val original = RecordViewModel(
            restoredRepository,
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        original.moveToPreviousMonth()
        advanceUntilIdle()

        assertEquals(RecordTestMonth.toString(), handle.get<String>("record_month"))
        assertEquals("조회할 수 없는 연월이에요", original.uiState.value.errorMessage)
    }

    @Test
    fun `복원 날짜는 최초 조회에만 적용하고 일반 재검증은 최신 날짜를 선택한다`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "record_month" to RecordTestMonth.toString(),
                "record_selected_date" to RecordTestMonth.atDay(2).toString(),
            ),
        )
        val restored = RecordViewModel(
            FakePostRepository(),
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        assertEquals(RecordTestMonth.atDay(2), restored.uiState.value.selectedDate)

        restored.revalidate()
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(5), restored.uiState.value.selectedDate)
        assertEquals(RecordTestMonth.atDay(5).toString(), handle.get<String>("record_selected_date"))
    }

    @Test
    fun `명시적으로 연 날짜는 저장된 월과 날짜보다 우선하고 저장값도 갱신한다`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "record_month" to RecordTestMonth.toString(),
                "record_selected_date" to RecordTestMonth.atDay(2).toString(),
            ),
        )
        val restored = RecordViewModel(
            FakePostRepository(),
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        restored.openDate(RecordLatestMonth.atDay(1))
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, restored.uiState.value.month)
        assertEquals(RecordLatestMonth.atDay(1), restored.uiState.value.selectedDate)
        assertEquals(RecordLatestMonth.toString(), handle.get<String>("record_month"))
        assertEquals(RecordLatestMonth.atDay(1).toString(), handle.get<String>("record_selected_date"))
    }

    @Test
    fun `명시한 날짜의 월 목록을 조회하는 중에도 새 ViewModel에서 그 날짜로 복원한다`() = runTest {
        val handle = SavedStateHandle()
        val source = FakePostRepository()
        val original = RecordViewModel(
            source,
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        val gate = CompletableDeferred<HomeResult<List<YearMonth>>>()
        source.pendingMonthsResult = gate
        original.openDate(RecordLatestMonth.atDay(1))
        advanceUntilIdle()

        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = RecordViewModel(
            FakePostRepository(),
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = SavedStateHandle(snapshot),
        )
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, restored.uiState.value.month)
        assertEquals(RecordLatestMonth.atDay(1), restored.uiState.value.selectedDate)
        gate.complete(HomeResult.Success(listOf(RecordTestMonth, RecordLatestMonth)))
        advanceUntilIdle()
    }

    @Test
    fun `월 목록 조회가 실패해도 달력 응답 대기 중 명시한 날짜를 복원한다`() = runTest {
        val handle = SavedStateHandle()
        val source = FakePostRepository()
        val original = RecordViewModel(
            source,
            initialMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        source.pendingMonthsResult = CompletableDeferred(HomeResult.Failure(HomeFailure.Network))
        val gate = CompletableDeferred<HomeResult<PostCalendar>>()
        source.pendingCalendarResult = gate
        original.openDate(RecordTestMonth.atDay(2))
        advanceUntilIdle()

        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = RecordViewModel(
            FakePostRepository(),
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = SavedStateHandle(snapshot),
        )
        advanceUntilIdle()

        assertEquals(RecordTestMonth, restored.uiState.value.month)
        assertEquals(RecordTestMonth.atDay(2), restored.uiState.value.selectedDate)
        gate.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun `캐시 재검증과 늦은 응답은 현재 화면의 저장 월과 날짜를 되돌리지 않는다`() = runTest {
        val handle = SavedStateHandle()
        val source = FakePostRepository()
        val original = RecordViewModel(
            source,
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        val oldResponse = source.getPostCalendar(RecordTestMonth)
        original.moveToNextMonth()
        advanceUntilIdle()
        val gate = CompletableDeferred<HomeResult<PostCalendar>>()
        source.pendingCalendarResult = gate
        original.moveToPreviousMonth()
        original.selectDate(RecordTestMonth.atDay(2))
        assertEquals(RecordTestMonth.atDay(2).toString(), handle.get<String>("record_selected_date"))

        source.pendingCalendarResult = null
        original.moveToNextMonth()
        advanceUntilIdle()
        gate.complete(oldResponse)
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, original.uiState.value.month)
        assertEquals(RecordLatestMonth.toString(), handle.get<String>("record_month"))
        assertEquals(RecordLatestMonth.atDay(1).toString(), handle.get<String>("record_selected_date"))
    }

    @Test
    fun `캐시에서 선택한 게시물 삭제 후 재검증해도 삭제 날짜를 복원하지 않는다`() = runTest {
        val handle = SavedStateHandle()
        val source = FakePostRepository()
        val original = RecordViewModel(
            source,
            initialMonth = RecordTestMonth,
            currentMonthProvider = { RecordLatestMonth },
            savedStateHandle = handle,
        )
        advanceUntilIdle()
        val oldResponse = source.getPostCalendar(RecordTestMonth)
        original.moveToNextMonth()
        advanceUntilIdle()
        val gate = CompletableDeferred<HomeResult<PostCalendar>>()
        source.pendingCalendarResult = gate
        original.moveToPreviousMonth()
        original.selectDate(RecordTestMonth.atDay(5))
        original.removeDeletedPost("post-5")
        gate.complete(oldResponse)
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), original.uiState.value.selectedDate)
        assertEquals(RecordTestMonth.atDay(2).toString(), handle.get<String>("record_selected_date"))
    }

    @Test
    fun loadsInitialMonth() = runTest {
        advanceUntilIdle()

        assertEquals(RecordTestMonth, viewModel.uiState.value.month)
        assertEquals(
            5,
            viewModel.uiState.value.selectedDate
                ?.dayOfMonth,
        )
        assertEquals(2, viewModel.uiState.value.posts.size)
        assertEquals(listOf(RecordTestMonth), repository.requests)
    }

    @Test
    fun opensRequestedDateAfterInitialLoading() = runTest {
        val date = RecordTestMonth.atDay(2)
        viewModel.openDate(date)
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.selectedDate)
        assertEquals(
            "post-2",
            viewModel.uiState.value.selectedPost
                ?.postId,
        )
    }

    @Test
    fun opensRequestedDateInAnotherMonthAndSupportsReusedRecordScreen() = runTest {
        advanceUntilIdle()
        viewModel.openDate(RecordLatestMonth.atDay(1))
        advanceUntilIdle()
        assertEquals(RecordLatestMonth, viewModel.uiState.value.month)
        assertEquals(RecordLatestMonth.atDay(1), viewModel.uiState.value.selectedDate)

        viewModel.openDate(RecordTestMonth.atDay(2))
        advanceUntilIdle()
        assertEquals(RecordTestMonth, viewModel.uiState.value.month)
        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun retainsRequestedDateDuringResumeRevalidation() = runTest {
        advanceUntilIdle()
        viewModel.openDate(RecordTestMonth.atDay(2))
        viewModel.onResume()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun loadsRequestedDateAfterSuspendedCalendarResponse() = runTest {
        advanceUntilIdle()
        val result = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = result
        val date = RecordLatestMonth.atDay(1)
        viewModel.openDate(date)
        assertEquals(true, viewModel.uiState.value.isLoading)
        result.complete(
            HomeResult.Success(
                PostCalendar(
                    month = RecordLatestMonth,
                    posts = listOf(
                        PostCalendarItem("requested", date, "photo", PostStatus.APPROVED),
                        PostCalendarItem("later", date.plusDays(1), "photo-later", PostStatus.APPROVED),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.selectedDate)
        assertEquals(
            "requested",
            viewModel.uiState.value.selectedPost
                ?.postId,
        )
    }

    @Test
    fun doesNotSelectAnotherPhotoWhenRequestedDateIsMissing() = runTest {
        advanceUntilIdle()
        viewModel.openDate(RecordTestMonth.atDay(3))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.posts.size)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals(null, viewModel.uiState.value.selectedPost)
    }

    @Test
    fun retainsRequestedMonthAndDateAfterFailureAndRetry() = runTest {
        advanceUntilIdle()
        val date = RecordLatestMonth.atDay(1)
        repository.failureMonth = RecordLatestMonth
        viewModel.openDate(date)
        advanceUntilIdle()
        assertEquals(RecordLatestMonth, viewModel.uiState.value.month)
        assertEquals(null, viewModel.uiState.value.selectedPost)

        repository.failureMonth = null
        viewModel.retryCurrentMonth()
        advanceUntilIdle()
        assertEquals(date, viewModel.uiState.value.selectedDate)
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

        assertEquals(RecordTestMonth.atDay(5), viewModel.uiState.value.selectedDate)
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
    fun `고른 달로 이동하면 그 달의 기록을 불러온다`() = runTest {
        advanceUntilIdle()

        viewModel.moveToMonth(RecordLatestMonth)
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, viewModel.uiState.value.month)
        assertEquals(RecordLatestMonth, repository.requests.last())
    }

    @Test
    fun `기록이 없는 달이나 지금 달을 고르면 다시 불러오지 않는다`() = runTest {
        advanceUntilIdle()
        val requestCount = repository.requests.size

        viewModel.moveToMonth(RecordTestMonth.minusMonths(5))
        viewModel.moveToMonth(RecordTestMonth)
        advanceUntilIdle()

        assertEquals(RecordTestMonth, viewModel.uiState.value.month)
        assertEquals(requestCount, repository.requests.size)
    }

    @Test
    fun `기록을 불러오는 중에는 고른 달로 이동하지 않는다`() = runTest {
        advanceUntilIdle()
        repository.pendingCalendarResult = CompletableDeferred()
        viewModel.moveToPreviousMonth()
        val requestCount = repository.requests.size

        viewModel.moveToMonth(RecordLatestMonth)

        assertEquals(RecordTestMonth.minusMonths(1), viewModel.uiState.value.month)
        assertEquals(false, viewModel.uiState.value.canPickMonth)
        assertEquals(requestCount, repository.requests.size)
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
    fun cachedMonthIsVisibleBeforeRevalidationAndReplacedOnSuccess() = runTest {
        val pending = returnToCachedMonth()
        val cachedPosts = viewModel.uiState.value.posts

        assertEquals(listOf("post-2", "post-5"), cachedPosts.map { it.postId })
        assertEquals(RecordTestMonth.atDay(5), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(listOf(RecordTestMonth, RecordLatestMonth, RecordTestMonth), repository.requests)

        val updated = cachedPosts.last().copy(status = PostStatus.PENDING, thumbnailImageUrl = "updated")
        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, listOf(updated))))
        advanceUntilIdle()

        assertEquals(listOf(updated), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.errorMessage)

        repository.pendingCalendarResult = null
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        repository.pendingCalendarResult = CompletableDeferred()
        viewModel.moveToPreviousMonth()
        assertEquals(listOf(updated), viewModel.uiState.value.posts)
        repository.pendingCalendarResult!!.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun cachedMonthFailureKeepsPostsAndSelectionAndShowsToast() = runTest {
        val pending = returnToCachedMonth()
        val cachedPosts = viewModel.uiState.value.posts
        viewModel.selectDate(RecordTestMonth.atDay(2))

        pending.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()

        assertEquals(cachedPosts, viewModel.uiState.value.posts)
        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals("기록을 불러오지 못했어요", (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text)
    }

    @Test
    fun cachedMonthUnauthorizedShowsLoginRequiredInsteadOfCachedPosts() = runTest {
        val pending = returnToCachedMonth()

        pending.complete(HomeResult.Failure(HomeFailure.Unauthorized))
        advanceUntilIdle()

        assertEquals(emptyList<PostCalendarItem>(), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals("로그인이 필요해요", viewModel.uiState.value.errorMessage)
        assertEquals(true, viewModel.uiState.value.isLoginRequired)
    }

    @Test
    fun cachedMonthExceptionDoesNotRestoreDeletedPosts() = runTest {
        val pending = returnToCachedMonth()
        viewModel.removeDeletedPost("post-5")

        pending.completeExceptionally(IllegalStateException("offline"))
        advanceUntilIdle()

        assertEquals(
            listOf("post-2"),
            viewModel.uiState.value.posts
                .map { it.postId },
        )
        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
        assertEquals("offline", (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text)
    }

    @Test
    fun uncachedMonthClearsContentWhileLoading() = runTest {
        advanceUntilIdle()
        val pending = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = pending

        viewModel.moveToNextMonth()

        assertEquals(emptyList<PostCalendarItem>(), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals(true, viewModel.uiState.value.isLoading)
        pending.complete(HomeResult.Success(PostCalendar(RecordLatestMonth, emptyList())))
        advanceUntilIdle()
    }

    @Test
    fun deletingPostAlsoRemovesItFromInactiveMonthCache() = runTest {
        advanceUntilIdle()
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        viewModel.removeDeletedPost("post-5")
        val pending = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = pending

        viewModel.moveToPreviousMonth()

        assertEquals(
            listOf("post-2"),
            viewModel.uiState.value.posts
                .map { it.postId },
        )
        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
        pending.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun cachedMonthRefreshPreservesDateSelectedDuringRequest() = runTest {
        val pending = returnToCachedMonth()
        val posts = viewModel.uiState.value.posts
        viewModel.selectDate(RecordTestMonth.atDay(2))

        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, posts)))
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun cachedMonthRefreshSelectsLatestDateWhenSelectionWasNotChanged() = runTest {
        val pending = returnToCachedMonth()
        val newest = viewModel.uiState.value.posts
            .last()
            .copy(topicDate = RecordTestMonth.atDay(7))

        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, listOf(newest))))
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(7), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun cachedMonthRefreshFallsBackWhenSelectedDateDisappears() = runTest {
        val pending = returnToCachedMonth()
        val remaining = viewModel.uiState.value.posts
            .last()
        viewModel.selectDate(RecordTestMonth.atDay(2))

        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, listOf(remaining))))
        advanceUntilIdle()

        assertEquals(RecordTestMonth.atDay(5), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun lateCachedMonthResponseDoesNotReplaceNewerMonth() = runTest {
        val pending = returnToCachedMonth()
        repository.pendingCalendarResult = null
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        val currentState = viewModel.uiState.value

        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, emptyList())))
        advanceUntilIdle()

        assertEquals(currentState, viewModel.uiState.value)
    }

    @Test
    fun lateResponseDoesNotAllowDuplicateRevalidationOfCurrentMonth() = runTest {
        val previousRequest = returnToCachedMonth()
        val currentRequest = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = currentRequest
        viewModel.moveToNextMonth()
        previousRequest.complete(HomeResult.Success(PostCalendar(RecordTestMonth, emptyList())))
        advanceUntilIdle()
        val requestCount = repository.requests.size

        viewModel.revalidate()

        assertEquals(requestCount, repository.requests.size)
        currentRequest.complete(HomeResult.Success(PostCalendar(RecordLatestMonth, emptyList())))
        advanceUntilIdle()
    }

    @Test
    fun emptyCachedMonthIsVisibleWithoutLoading() = runTest {
        advanceUntilIdle()
        viewModel.moveToPreviousMonth()
        advanceUntilIdle()
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        val pending = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = pending

        viewModel.moveToPreviousMonth()

        assertEquals(emptyList<PostCalendarItem>(), viewModel.uiState.value.posts)
        assertEquals(null, viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.isLoading)
        pending.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun pendingSuccessCannotRestoreDeletedPostInScreenOrCache() = runTest {
        val pending = returnToCachedMonth()
        val oldPosts = viewModel.uiState.value.posts
        viewModel.removeDeletedPost("post-5")
        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, oldPosts)))
        advanceUntilIdle()

        assertEquals(
            listOf("post-2"),
            viewModel.uiState.value.posts
                .map { it.postId },
        )
        repository.pendingCalendarResult = null
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        val nextRequest = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = nextRequest
        viewModel.moveToPreviousMonth()

        assertEquals(
            listOf("post-2"),
            viewModel.uiState.value.posts
                .map { it.postId },
        )
        nextRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun lateSuccessUpdatesInactiveMonthCacheWithoutChangingScreen() = runTest {
        val pending = returnToCachedMonth()
        val updated = viewModel.uiState.value.posts
            .last()
            .copy(status = PostStatus.PENDING)
        repository.pendingCalendarResult = null
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        val currentState = viewModel.uiState.value

        pending.complete(HomeResult.Success(PostCalendar(RecordTestMonth, listOf(updated))))
        advanceUntilIdle()
        assertEquals(currentState, viewModel.uiState.value)
        val nextRequest = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = nextRequest
        viewModel.moveToPreviousMonth()

        assertEquals(listOf(updated), viewModel.uiState.value.posts)
        nextRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun olderSuccessDoesNotOverwriteNewerResponseForSameMonth() = runTest {
        val olderRequest = returnToCachedMonth()
        repository.pendingCalendarResult = null
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        viewModel.moveToPreviousMonth()
        advanceUntilIdle()
        val newerPosts = viewModel.uiState.value.posts

        olderRequest.complete(HomeResult.Success(PostCalendar(RecordTestMonth, emptyList())))
        advanceUntilIdle()
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        val nextRequest = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = nextRequest
        viewModel.moveToPreviousMonth()

        assertEquals(newerPosts, viewModel.uiState.value.posts)
        nextRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    private fun TestScope.returnToCachedMonth(): CompletableDeferred<HomeResult<PostCalendar>> {
        advanceUntilIdle()
        viewModel.moveToNextMonth()
        advanceUntilIdle()
        return CompletableDeferred<HomeResult<PostCalendar>>().also {
            repository.pendingCalendarResult = it
            viewModel.moveToPreviousMonth()
        }
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
    fun `재진입 재검증은 기존 기록을 유지한 채 최신 응답으로 교체한다`() = runTest {
        advanceUntilIdle()
        val pendingResult = CompletableDeferred<HomeResult<PostCalendar>>()
        repository.pendingCalendarResult = pendingResult
        val previousPosts = viewModel.uiState.value.posts

        viewModel.onResume()
        viewModel.onResume()

        assertEquals(previousPosts, viewModel.uiState.value.posts)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(true, viewModel.uiState.value.canGoPrevious)
        assertEquals(2, repository.requests.size)

        pendingResult.complete(
            HomeResult.Success(
                PostCalendar(
                    month = RecordTestMonth,
                    posts = listOf(
                        PostCalendarItem(
                            postId = "post-7",
                            topicDate = RecordTestMonth.atDay(7),
                            thumbnailImageUrl = "photo-7",
                            status = PostStatus.APPROVED,
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("post-7"),
            viewModel.uiState.value.posts
                .map(PostCalendarItem::postId),
        )
    }

    @Test
    fun `재진입 재검증 실패는 기존 기록을 유지하고 Toast 메시지를 보낸다`() = runTest {
        advanceUntilIdle()
        repository.pendingCalendarResult = CompletableDeferred<HomeResult<PostCalendar>>().apply {
            complete(HomeResult.Failure(HomeFailure.Network))
        }
        val previousPosts = viewModel.uiState.value.posts

        viewModel.onResume()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(previousPosts, viewModel.uiState.value.posts)
        assertEquals(
            "기록을 불러오지 못했어요",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
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
    fun movesToCurrentMonthEvenWhenMonthListResponseOmitsIt() = runTest {
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.canGoNext)
        assertEquals(
            listOf(RecordLatestMonth, RecordTestMonth, RecordTestMonth.minusMonths(1)),
            viewModel.uiState.value.availableMonths,
        )
        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(RecordLatestMonth, viewModel.uiState.value.month)
        assertEquals(false, viewModel.uiState.value.canGoNext)

        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(listOf(RecordTestMonth, RecordLatestMonth), repository.requests)
    }

    @Test
    fun currentMonthRemainsAvailableAfterItsLastPostIsDeleted() = runTest {
        val currentRepository = FakePostRepository()
        val currentMonthViewModel = RecordViewModel(
            repository = currentRepository,
            initialMonth = RecordLatestMonth,
            latestMonth = RecordLatestMonth,
            currentMonthProvider = { RecordLatestMonth },
        )
        advanceUntilIdle()

        currentMonthViewModel.removeDeletedPost("post-1")

        assertEquals(emptyList<PostCalendarItem>(), currentMonthViewModel.uiState.value.posts)
        assertEquals(
            listOf(RecordLatestMonth, RecordTestMonth, RecordTestMonth.minusMonths(1)),
            currentMonthViewModel.uiState.value.availableMonths,
        )
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
    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

    val requests = mutableListOf<YearMonth>()
    var failureMonth: YearMonth? = null
    var unauthorizedMonth: YearMonth? = null
    var pendingCalendarResult: CompletableDeferred<HomeResult<PostCalendar>>? = null
    var pendingMonthsResult: CompletableDeferred<HomeResult<List<YearMonth>>>? = null

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> {
        requests += month
        pendingCalendarResult?.let { return it.await() }
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
                } else if (month == RecordLatestMonth) {
                    listOf(calendarPost(month, day = 1))
                } else {
                    emptyList()
                },
            ),
        )
    }

    override suspend fun getPostCalendarMonths(): HomeResult<List<YearMonth>> = pendingMonthsResult?.await()
        ?: HomeResult.Success(
            listOf(RecordLatestMonth, RecordTestMonth, RecordTestMonth.minusMonths(1)),
        )

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

    override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

    override suspend fun updatePostTitle(
        postId: String,
        title: String?,
    ): HomeResult<com.stonefive.chalkak.domain.model.PostTitleUpdate> = error("unused")

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
