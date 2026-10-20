package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `today and yesterday rankings request popular and preserve server order`() = runTest {
        val repository = FakeHomeRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf(HomeQuery(TODAY, PostSort.POPULAR, 1)), repository.contentQueries)
        assertEquals(listOf(HomeQuery(TODAY.minusDays(1), PostSort.POPULAR, 1)), repository.pageQueries)
        assertEquals(
            listOf("rank-1", "rank-2"),
            viewModel.uiState.value.trending.photos
                .map { it.id },
        )
        assertEquals(HomeSectionStatus.Ready, viewModel.uiState.value.yesterday.status)
    }

    @Test
    fun `Sunday to Saturday week crosses year boundary and includes pending records`() = runTest {
        val date = LocalDate.of(2027, 1, 1)
        val repository = FakeHomeRepository().apply {
            records = listOf(
                record(LocalDate.of(2026, 12, 27), PostStatus.APPROVED),
                record(date, PostStatus.PENDING),
            )
        }
        val viewModel = createViewModel(repository, dateProvider = { date })
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), repository.months)
        assertEquals(
            LocalDate.of(2026, 12, 27),
            state.week
                .first()
                .date,
        )
        assertEquals(
            LocalDate.of(2027, 1, 2),
            state.week
                .last()
                .date,
        )
        assertEquals(2, state.week.count { it.post != null })
        assertEquals(HomeHeroState.Photo("thumb-$date", isPending = true), state.hero)
        assertTrue(repository.details.isEmpty())
    }

    @Test
    fun `Sunday starts new week`() {
        val sunday = LocalDate.of(2026, 10, 4)
        assertEquals(sunday, homeWeekDates(sunday).first())
        assertEquals(sunday.plusDays(6), homeWeekDates(sunday).last())
    }

    @Test
    fun `approved hero loads original and signature by calendar id outside ranking page`() = runTest {
        val repository = FakeHomeRepository().apply { records = listOf(record(TODAY, PostStatus.APPROVED)) }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf("own-$TODAY"), repository.details)
        assertEquals(
            HomeHeroState.Photo("original-own-$TODAY", "thumb-$TODAY", "signature", "signature-thumb"),
            viewModel.uiState.value.hero,
        )
    }

    @Test
    fun `approved detail failure preserves calendar photo`() = runTest {
        val repository = FakeHomeRepository().apply {
            records = listOf(record(TODAY, PostStatus.APPROVED))
            detailFailure = true
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeHeroState.Photo("thumb-$TODAY"), viewModel.uiState.value.hero)
    }

    @Test
    fun `validating upload is processing rather than empty`() = runTest {
        val repository = FakeHomeRepository().apply {
            uploadStatus = TodayPostStatusResult.Success(
                TodayPostStatus(TODAY, true, "validating", TodayPostModerationStatus.VALIDATING),
            )
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeHeroState.Processing, viewModel.uiState.value.hero)
    }

    @Test
    fun `no uploaded photo shows empty hero`() = runTest {
        val viewModel = createViewModel(FakeHomeRepository())
        advanceUntilIdle()
        assertEquals(HomeHeroState.Empty, viewModel.uiState.value.hero)
    }

    @Test
    fun `missing today topic is ready for guest without private requests`() = runTest {
        val repository = FakeHomeRepository().apply { todayFailure = HomeFailure.TopicNotFound }
        val viewModel = createViewModel(repository, MutableStateFlow(UserSessionState.Guest))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("", state.topic)
        assertEquals(HomeSectionStatus.Ready, state.topicStatus)
        assertEquals(HomeRankingState(HomeSectionStatus.Ready), state.trending)
        assertTrue(state.isInitialLoadComplete)
        assertEquals(0, repository.statusCalls)
    }

    @Test
    fun `no open topic does not invite upload even when today topic exists`() = runTest {
        val repository = FakeHomeRepository().apply {
            uploadStatus = TodayPostStatusResult.Failure(TodayPostStatusFailure.NoOpenTopic)
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals("하늘", viewModel.uiState.value.topic)
        assertEquals(HomeHeroState.NoOpenTopic, viewModel.uiState.value.hero)
    }

    @Test
    fun `today network failure remains an error`() = runTest {
        val repository = FakeHomeRepository().apply { todayFailure = HomeFailure.Network }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeSectionStatus.Error, viewModel.uiState.value.topicStatus)
        assertEquals(HomeSectionStatus.Error, viewModel.uiState.value.trending.status)
    }

    @Test
    fun `refresh after topic opens restores topic and empty upload slot`() = runTest {
        val repository = FakeHomeRepository().apply {
            todayFailure = HomeFailure.TopicNotFound
            uploadStatus = TodayPostStatusResult.Failure(TodayPostStatusFailure.NoOpenTopic)
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeHeroState.NoOpenTopic, viewModel.uiState.value.hero)
        repository.todayFailure = null
        repository.uploadStatus = TodayPostStatusResult.Success(TodayPostStatus(TODAY, false, null, null))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("하늘", viewModel.uiState.value.topic)
        assertEquals(HomeSectionStatus.Ready, viewModel.uiState.value.topicStatus)
        assertEquals(HomeHeroState.Empty, viewModel.uiState.value.hero)
    }

    @Test
    fun `calendar failure cannot masquerade as no upload or erase public rankings`() = runTest {
        val repository = FakeHomeRepository().apply { calendarFailure = true }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeSectionStatus.Error, viewModel.uiState.value.recordsStatus)
        assertEquals(HomeHeroState.Error, viewModel.uiState.value.hero)
        assertEquals(HomeSectionStatus.Ready, viewModel.uiState.value.trending.status)
    }

    @Test
    fun `yesterday failure leaves personal photo and today ranking usable`() = runTest {
        val repository = FakeHomeRepository().apply {
            records = listOf(record(TODAY, PostStatus.PENDING))
            yesterdayFailure = HomeFailure.Network
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(HomeSectionStatus.Error, viewModel.uiState.value.yesterday.status)
        assertEquals(HomeSectionStatus.Ready, viewModel.uiState.value.trending.status)
        assertTrue(viewModel.uiState.value.hero is HomeHeroState.Photo)
    }

    @Test
    fun `guest does not call private APIs and logout clears owned photo`() = runTest {
        val repository = FakeHomeRepository().apply { records = listOf(record(TODAY, PostStatus.PENDING)) }
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        val viewModel = createViewModel(repository, session)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hero is HomeHeroState.Photo)
        val calls = repository.months.size
        session.value = UserSessionState.Guest
        advanceUntilIdle()
        assertEquals(calls, repository.months.size)
        assertEquals(0, repository.statusCalls)
        assertEquals(HomeHeroState.Empty, viewModel.uiState.value.hero)
        assertTrue(
            viewModel.uiState.value.week
                .all { it.post == null },
        )
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun `signed out session waits before preloading home APIs`() = runTest {
        val repository = FakeHomeRepository()
        val session = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)
        val viewModel = createViewModel(repository, session)
        advanceUntilIdle()

        assertTrue(repository.contentQueries.isEmpty())
        assertTrue(repository.pageQueries.isEmpty())
        assertTrue(repository.months.isEmpty())

        session.value = UserSessionState.Guest
        advanceUntilIdle()

        assertEquals(listOf(HomeQuery(TODAY, PostSort.POPULAR, 1)), repository.contentQueries)
        assertEquals(listOf(HomeQuery(TODAY.minusDays(1), PostSort.POPULAR, 1)), repository.pageQueries)
        assertTrue(repository.months.isEmpty())
        assertTrue(viewModel.isReadyForInitialDisplay.value)
    }

    @Test
    fun `initial display waits for home preload to finish`() = runTest {
        val repository = FakeHomeRepository().apply { calendarGate = CompletableDeferred() }
        val viewModel = createViewModel(repository)
        runCurrent()

        assertFalse(viewModel.isReadyForInitialDisplay.value)

        repository.calendarGate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.isReadyForInitialDisplay.value)
    }

    @Test
    fun `initial display is released after deadline when session stays loading`() = runTest {
        val repository = FakeHomeRepository()
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
        val viewModel = createViewModel(repository, session)
        runCurrent()
        advanceTimeBy(699)
        runCurrent()
        assertFalse(viewModel.isReadyForInitialDisplay.value)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(viewModel.isReadyForInitialDisplay.value)
        assertEquals(UserSessionState.Loading, session.value)
        assertTrue(repository.contentQueries.isEmpty())
        assertTrue(repository.months.isEmpty())

        session.value = UserSessionState.Authenticated("user")
        advanceUntilIdle()
        assertEquals(listOf(HomeQuery(TODAY, PostSort.POPULAR, 1)), repository.contentQueries)
        assertTrue(viewModel.uiState.value.isInitialLoadComplete)
        assertTrue(viewModel.isReadyForInitialDisplay.value)
    }

    @Test
    fun `session and home loading share initial display deadline`() = runTest {
        val repository = FakeHomeRepository().apply { calendarGate = CompletableDeferred() }
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
        val viewModel = createViewModel(repository, session)
        runCurrent()
        advanceTimeBy(500)
        session.value = UserSessionState.Authenticated("user")
        runCurrent()
        advanceTimeBy(199)
        runCurrent()
        assertFalse(viewModel.isReadyForInitialDisplay.value)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(viewModel.isReadyForInitialDisplay.value)
        assertFalse(viewModel.uiState.value.isInitialLoadComplete)

        repository.calendarGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInitialLoadComplete)
    }

    @Test
    fun `first presentation after KST midnight refreshes preloaded content`() = runTest {
        var date = LocalDate.of(2026, 10, 10)
        val repository = FakeHomeRepository()
        val viewModel = createViewModel(repository, dateProvider = { date })
        advanceUntilIdle()

        date = date.plusDays(1)
        repository.records = listOf(record(date, PostStatus.PENDING))
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.date)
        assertEquals(
            date,
            viewModel.uiState.value.week
                .first()
                .date,
        )
        assertEquals(HomeHeroState.Photo("thumb-$date", isPending = true), viewModel.uiState.value.hero)
        assertEquals(listOf(date.minusDays(1), date), repository.contentQueries.map { it.date })
        assertEquals(listOf(date.minusDays(2), date.minusDays(1)), repository.pageQueries.map { it.date })
    }

    @Test
    fun `feedback start moves to processing and completes after mock duration`() = runTest {
        val viewModel = createViewModel(FakeHomeRepository())
        advanceUntilIdle()
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)

        viewModel.startFeedback()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        advanceTimeBy(HomeViewModel.MOCK_FEEDBACK_DURATION_MS - 1)
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)

        viewModel.startFeedback()
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
    }

    @Test
    fun `crossing KST midnight during feedback resets it for the new day`() = runTest {
        var date = LocalDate.of(2026, 10, 10)
        val viewModel = createViewModel(FakeHomeRepository(), dateProvider = { date })
        advanceUntilIdle()
        viewModel.onResume()
        viewModel.startFeedback()

        date = date.plusDays(1)
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.value.date)
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
    }

    @Test
    fun `returning from upload refreshes and crossing KST midnight moves week`() = runTest {
        var date = LocalDate.of(2026, 10, 10)
        val repository = FakeHomeRepository()
        val viewModel = createViewModel(repository, dateProvider = { date })
        advanceUntilIdle()
        viewModel.onResume()
        assertEquals(1, repository.contentQueries.size)
        date = date.plusDays(1)
        repository.records = listOf(record(date, PostStatus.PENDING))
        viewModel.onResume()
        advanceUntilIdle()
        assertEquals(date, viewModel.uiState.value.date)
        assertEquals(
            date,
            viewModel.uiState.value.week
                .first()
                .date,
        )
        assertTrue((viewModel.uiState.value.hero as HomeHeroState.Photo).isPending)
        assertEquals(2, repository.contentQueries.size)
    }

    @Test
    fun `returning home keeps cached content without refresh indicator and updates silently`() = runTest {
        val repository = FakeHomeRepository().apply {
            records = listOf(record(TODAY, PostStatus.PENDING))
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.onResume()
        val cached = viewModel.uiState.value
        repository.calendarGate = CompletableDeferred()
        repository.records = emptyList()

        viewModel.onResume()
        advanceUntilIdle()
        assertEquals(cached.hero, viewModel.uiState.value.hero)
        assertEquals(cached.week, viewModel.uiState.value.week)
        assertEquals(cached.trending, viewModel.uiState.value.trending)
        assertFalse(viewModel.uiState.value.isRefreshing)

        repository.calendarGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(HomeHeroState.Empty, viewModel.uiState.value.hero)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `manual refresh shows indicator while preserving cached content`() = runTest {
        val repository = FakeHomeRepository().apply {
            records = listOf(record(TODAY, PostStatus.PENDING))
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val cached = viewModel.uiState.value
        repository.calendarGate = CompletableDeferred()

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(cached.hero, viewModel.uiState.value.hero)
        assertEquals(cached.week, viewModel.uiState.value.week)
        assertTrue(viewModel.uiState.value.isRefreshing)

        repository.calendarGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `late cancelled private request cannot repopulate guest state`() = runTest {
        val repository = FakeHomeRepository().apply {
            records = listOf(record(TODAY, PostStatus.PENDING))
            calendarGate = CompletableDeferred()
        }
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        val viewModel = createViewModel(repository, session)
        session.value = UserSessionState.Guest
        repository.calendarGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(HomeHeroState.Empty, viewModel.uiState.value.hero)
        assertTrue(
            viewModel.uiState.value.week
                .all { it.post == null },
        )
    }

    private fun createViewModel(
        repository: FakeHomeRepository,
        session: MutableStateFlow<UserSessionState> = MutableStateFlow(UserSessionState.Authenticated("user")),
        dateProvider: () -> LocalDate = { TODAY },
    ) = HomeViewModel(repository, repository, session, dateProvider).also {
        it.startPreloading()
    }
}

private class FakeHomeRepository :
    PostRepository,
    PhotoUploadEntryRepository {
    val contentQueries = mutableListOf<HomeQuery>()
    val pageQueries = mutableListOf<HomeQuery>()
    val months = mutableListOf<YearMonth>()
    val details = mutableListOf<String>()
    var statusCalls = 0
    var records = emptyList<PostCalendarItem>()
    var calendarFailure = false
    var detailFailure = false
    var todayFailure: HomeFailure? = null
    var yesterdayFailure: HomeFailure? = null
    var calendarGate: CompletableDeferred<Unit>? = null
    var uploadStatus: TodayPostStatusResult = TodayPostStatusResult.Success(TodayPostStatus(TODAY, false, null, null))

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        contentQueries += query
        todayFailure?.let { return HomeResult.Failure(it) }
        return HomeResult.Success(PostContent(query.date, "하늘", listOf(photo("rank-1"), photo("rank-2")), emptySet()))
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> {
        pageQueries += query
        return yesterdayFailure?.let { HomeResult.Failure(it) }
            ?: HomeResult.Success(PostPage(listOf(photo("yesterday")), emptySet(), 1, false, null))
    }

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> {
        months += month
        calendarGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
        return if (calendarFailure) {
            HomeResult.Failure(HomeFailure.Network)
        } else {
            HomeResult.Success(PostCalendar(month, records.filter { YearMonth.from(it.topicDate) == month }))
        }
    }

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> {
        details += postId
        return if (detailFailure) {
            HomeResult.Failure(HomeFailure.Network)
        } else {
            HomeResult.Success(PostDetail(photo(postId), "하늘", TODAY))
        }
    }

    override suspend fun getTodayPostStatus(): TodayPostStatusResult {
        statusCalls++
        return uploadStatus
    }

    override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

    override suspend fun updatePostTitle(
        postId: String,
        title: String?,
    ): HomeResult<PostTitleUpdate> = error("unused")

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = error("unused")
}

private val TODAY = LocalDate.of(2026, 10, 8)

private fun record(
    date: LocalDate,
    status: PostStatus,
) = PostCalendarItem("own-$date", date, "thumb-$date", status)

private fun photo(id: String) = Post(
    id = id,
    originalImageUrl = "original-$id",
    thumbnailImageUrl = "thumbnail-$id",
    signatureOriginalImageUrl = "signature",
    signatureThumbnailImageUrl = "signature-thumb",
    contentDescription = "사진 $id",
    title = null,
    likeCount = 10,
)
