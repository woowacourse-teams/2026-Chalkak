package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.BannerPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.PostUpdate
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.BannerRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAiReviewTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `completed review survives home reentry without another GET`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()

        repeat(2) {
            viewModel.onPause()
            viewModel.onResume()
            runCurrent()
            assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
            assertEquals(content(), viewModel.uiState.value.feedbackResult)
        }
        assertEquals(listOf(POST_ID), ai.getPostIds)
    }

    @Test
    fun `processing review stays visible while home reentry GET is pending`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.onPause()
        ai.getGate = CompletableDeferred()

        viewModel.onResume()
        runCurrent()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        assertEquals(listOf(POST_ID, POST_ID), ai.getPostIds)
        ai.getGate!!.complete(Unit)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
    }

    @Test
    fun `ready review stays visible while home reentry GET is pending`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.NONE)
            getResults += success(AiReview.Status.NONE)
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.onPause()
        ai.getGate = CompletableDeferred()

        viewModel.onResume()
        runCurrent()
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
        ai.getGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(POST_ID, POST_ID), ai.getPostIds)
    }

    @Test
    fun `completed review is cleared when post or account changes`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.COMPLETED, content = content())
            getResults += success(AiReview.Status.NONE)
            getResults += success(AiReview.Status.NONE)
        }
        val repository = FakeHomeRepository(approved = true)
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        val viewModel = HomeViewModel(
            repository = repository,
            uploadEntryRepository = repository,
            sessionState = session,
            bannerRepository = repository,
            aiReviewRepository = ai,
            dateProvider = { TODAY },
        ).also { it.startPreloading() }
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()

        repository.postId = "new-post"
        viewModel.refresh()
        runCurrent()
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
        assertNull(viewModel.uiState.value.feedbackResult)
        session.value = UserSessionState.Authenticated("other-user")
        runCurrent()
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
        assertNull(viewModel.uiState.value.feedbackResult)
        assertEquals(listOf(POST_ID, "new-post", "new-post"), ai.getPostIds)
    }

    @Test
    fun `preload does not request AI and NONE does not start POST`() = runTest {
        val ai = FakeAiReviewRepository().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        assertTrue(ai.getPostIds.isEmpty())
        assertEquals(HomeFeedbackPhase.Hidden, viewModel.uiState.value.feedbackPhase)

        viewModel.onResume()
        runCurrent()
        assertEquals(listOf(POST_ID), ai.getPostIds)
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
    }

    @Test
    fun `start feedback accepts one click and polls until completed`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(listOf(POST_ID), ai.requestPostIds)
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)

        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(1, ai.getPostIds.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(POST_ID, POST_ID), ai.getPostIds)
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
        assertEquals(content(), viewModel.uiState.value.feedbackResult)
    }

    @Test
    fun `processing stops polling after five minutes and remains processing`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            requestResults += success(AiReview.Status.PROCESSING)
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_WINDOW_MS)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        val calls = ai.getPostIds.size
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(calls, ai.getPostIds.size)
    }

    @Test
    fun `pause cancels polling and resume starts a new GET window`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        viewModel.onPause()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, ai.getPostIds.size)
        viewModel.onResume()
        runCurrent()
        assertEquals(2, ai.getPostIds.size)
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
    }

    @Test
    fun `failed review shows failure and is never automatically retried`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(
                AiReview.Status.FAILED,
                canRequestAgain = true,
            )
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        assertEquals(HomeFeedbackPhase.Failed, viewModel.uiState.value.feedbackPhase)
        assertTrue(viewModel.uiState.value.feedbackCanRequestAgain)
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `failed review is requested again only while retry is left`() = runTest {
        for (canRequestAgain in listOf(true, false)) {
            val ai = FakeAiReviewRepository().apply {
                getResults += success(AiReview.Status.FAILED, canRequestAgain = canRequestAgain)
                requestResults += success(AiReview.Status.PROCESSING)
            }
            val viewModel = createViewModel(ai)
            advanceUntilIdle()
            viewModel.onResume()
            runCurrent()
            assertEquals(HomeFeedbackPhase.Failed, viewModel.uiState.value.feedbackPhase)
            assertEquals(canRequestAgain, viewModel.uiState.value.feedbackCanRequestAgain)
            viewModel.startFeedback()
            runCurrent()
            assertEquals(if (canRequestAgain) 1 else 0, ai.requestPostIds.size)
            assertEquals(
                if (canRequestAgain) HomeFeedbackPhase.Processing else HomeFeedbackPhase.Failed,
                viewModel.uiState.value.feedbackPhase,
            )
        }
    }

    @Test
    fun `completed review cannot be requested again`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `active polling network failure retains processing and keeps polling`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += failure(AiReviewFailure.Network)
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        assertTrue(viewModel.uiState.value.feedbackMessage != null)
        val calls = ai.getPostIds.size
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_INTERVAL_MS * 2)
        runCurrent()
        assertEquals(calls + 2, ai.getPostIds.size)
    }

    @Test
    fun `active polling unauthorized failure stops polling`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += failure(AiReviewFailure.Http(401, null, null))
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)
        val calls = ai.getPostIds.size
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_INTERVAL_MS * 2)
        runCurrent()
        assertEquals(calls, ai.getPostIds.size)
    }

    @Test
    fun `late GET response cannot repopulate after pause account switch or post change`() = runTest {
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.PROCESSING)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val repository = FakeHomeRepository(approved = true)
        val viewModel = HomeViewModel(
            repository = repository,
            uploadEntryRepository = repository,
            sessionState = session,
            bannerRepository = repository,
            aiReviewRepository = ai,
            dateProvider = { TODAY },
        ).also { it.startPreloading() }
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        ai.getGate = CompletableDeferred()
        advanceTimeBy(HomeViewModel.FEEDBACK_POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(2, ai.getPostIds.size)

        viewModel.onPause()
        ai.getGate!!.complete(Unit)
        runCurrent()
        assertEquals(HomeFeedbackPhase.Processing, viewModel.uiState.value.feedbackPhase)

        // A session change and a subsequent home refresh must not allow the old request to win.
        session.value = UserSessionState.Authenticated("other-user")
        runCurrent()
        repository.approved = false
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(HomeFeedbackPhase.Hidden, viewModel.uiState.value.feedbackPhase)
        assertNull(viewModel.uiState.value.feedbackResult)
    }

    @Test
    fun `missing or unapproved hero never calls AI`() = runTest {
        val ai = FakeAiReviewRepository()
        val viewModel = createViewModel(ai, approved = false)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        assertTrue(ai.getPostIds.isEmpty())
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `GET failure stops polling and refresh retries`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += failure(AiReviewFailure.Network)
            getResults += success(AiReview.Status.NONE)
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        assertEquals(1, ai.getPostIds.size)
        assertTrue(viewModel.uiState.value.feedbackMessage != null)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, ai.getPostIds.size)
    }

    @Test
    fun `POST HTTP failure exposes server message and does not retry`() = runTest {
        val message = "지금은 AI 리뷰를 요청할 수 없습니다."
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += failure(AiReviewFailure.Http(400, "BUSINESS_ERROR", message))
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(message, viewModel.uiState.value.feedbackMessage)
        assertEquals(listOf(POST_ID), ai.requestPostIds)
    }

    @Test
    fun `unknown POST outcome synchronizes with GET without auto POST`() = runTest {
        val ai = FakeAiReviewRepository().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += failure(AiReviewFailure.Network)
            getResults += success(AiReview.Status.COMPLETED, content = content())
        }
        val viewModel = createViewModel(ai)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(listOf(POST_ID), ai.requestPostIds)
        assertEquals(listOf(POST_ID, POST_ID), ai.getPostIds)
        assertEquals(HomeFeedbackPhase.Completed, viewModel.uiState.value.feedbackPhase)
        assertNull(viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun `오늘 사진을 올리기 전이면 업로드 전 안내 카드를 보여 준다`() = runTest {
        // Given 로그인, AI 리뷰 켜짐, 오늘 올린 사진 없음
        val ai = FakeAiReviewRepository()
        val viewModel = createViewModel(ai, approved = false)
        // When  홈을 불러온다
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        // Then  피드백 카드가 업로드 전 안내 상태이고 AI 리뷰 조회는 0번
        assertEquals(HomeFeedbackNotice.BeforeUpload, viewModel.uiState.value.feedbackNotice)
        assertTrue(viewModel.uiState.value.showsFeedback)
        assertTrue(ai.getPostIds.isEmpty())
    }

    @Test
    fun `오늘 사진이 승인 대기면 승인 대기 안내 카드를 보여 준다`() = runTest {
        // Given 로그인, AI 리뷰 켜짐, 오늘 사진이 PENDING
        val ai = FakeAiReviewRepository()
        val repository = FakeHomeRepository(approved = false).apply { pending = true }
        val viewModel = createViewModel(ai, repository = repository)
        // When  홈을 불러온다
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        // Then  피드백 카드가 승인 대기 안내 상태이고 AI 리뷰 조회는 0번
        assertEquals(HomeFeedbackNotice.AwaitingApproval, viewModel.uiState.value.feedbackNotice)
        assertTrue(viewModel.uiState.value.showsFeedback)
        assertTrue(ai.getPostIds.isEmpty())
    }

    @Test
    fun `업로드 직후 검사 중이어도 승인 대기 안내 카드를 보여 준다`() = runTest {
        // Given 로그인, AI 리뷰 켜짐, 오늘 사진이 VALIDATING
        val ai = FakeAiReviewRepository()
        val repository = FakeHomeRepository(approved = false).apply {
            todayPostStatus = TodayPostStatusResult.Success(
                TodayPostStatus(TODAY, true, POST_ID, TodayPostModerationStatus.VALIDATING),
            )
        }
        val viewModel = createViewModel(ai, repository = repository)
        // When  홈을 불러온다
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        // Then  피드백 카드가 승인 대기 안내 상태
        assertEquals(HomeHeroState.Processing, viewModel.uiState.value.hero)
        assertEquals(HomeFeedbackNotice.AwaitingApproval, viewModel.uiState.value.feedbackNotice)
        assertTrue(viewModel.uiState.value.showsFeedback)
    }

    @Test
    fun `검사 중에 올린 사진을 보여 줄 때도 승인 대기 안내 카드를 보여 준다`() {
        // Given 오늘 사진이 VALIDATING이고 맨 위 카드가 보관한 업로드 사진을 보여 준다
        val hero = HomeHeroState.ProcessingPhoto("/cache/$POST_ID.webp")
        // When  승인 전 안내 종류를 정한다
        val notice = hero.feedbackNotice()
        // Then  검사 중 문구 카드와 같은 승인 대기 안내
        assertEquals(HomeFeedbackNotice.AwaitingApproval, notice)
    }

    @Test
    fun `안내 카드에서 피드백 시작을 보내도 AI 리뷰를 요청하지 않는다`() = runTest {
        // Given 피드백 카드가 업로드 전 안내 상태
        val ai = FakeAiReviewRepository()
        val viewModel = createViewModel(ai, approved = false)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        // When  피드백 시작을 보낸다
        viewModel.startFeedback()
        runCurrent()
        // Then  AI 리뷰 요청(POST)은 0번이고 카드는 업로드 전 안내 상태 그대로
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(HomeFeedbackNotice.BeforeUpload, viewModel.uiState.value.feedbackNotice)
        assertTrue(viewModel.uiState.value.showsFeedback)
    }

    @Test
    fun `승인되면 안내 대신 피드백 받기 카드로 바뀐다`() = runTest {
        // Given 오늘 사진이 PENDING이라 승인 대기 안내가 보이는 상태
        val ai = FakeAiReviewRepository().apply { getResults += success(AiReview.Status.NONE) }
        val repository = FakeHomeRepository(approved = false).apply { pending = true }
        val viewModel = createViewModel(ai, repository = repository)
        advanceUntilIdle()
        viewModel.onResume()
        runCurrent()
        assertEquals(HomeFeedbackNotice.AwaitingApproval, viewModel.uiState.value.feedbackNotice)
        // When  사진이 승인된 뒤 홈을 새로고침하고 AI 리뷰 조회가 NONE
        repository.pending = false
        repository.approved = true
        viewModel.refresh()
        advanceUntilIdle()
        // Then  피드백 카드가 "AI에게 피드백 받아보기"(요청 가능) 상태
        assertNull(viewModel.uiState.value.feedbackNotice)
        assertEquals(HomeFeedbackPhase.Ready, viewModel.uiState.value.feedbackPhase)
        assertTrue(viewModel.uiState.value.showsFeedback)
        assertEquals(listOf(POST_ID), ai.getPostIds)
    }

    @Test
    fun `비로그인이거나 AI 리뷰가 꺼져 있으면 사진 올리기 전에도 카드를 숨긴다`() = runTest {
        // Given 오늘 올린 사진 없음, (a) 비로그인 / (b) AI 리뷰 꺼짐
        val guest = createViewModel(
            FakeAiReviewRepository(),
            approved = false,
            session = MutableStateFlow(UserSessionState.Guest),
        )
        val disabled = createViewModel(FakeAiReviewRepository(), approved = false, isAiReviewEnabled = { false })
        // When  홈을 불러온다
        advanceUntilIdle()
        guest.onResume()
        disabled.onResume()
        runCurrent()
        // Then  피드백 카드가 숨김
        assertEquals(HomeHeroState.LoginRequired, guest.uiState.value.hero)
        assertNull(guest.uiState.value.feedbackNotice)
        assertFalse(guest.uiState.value.showsFeedback)
        assertEquals(HomeHeroState.Empty, disabled.uiState.value.hero)
        assertNull(disabled.uiState.value.feedbackNotice)
        assertFalse(disabled.uiState.value.showsFeedback)
    }

    @Test
    fun `반려·주제 없음·불러오기 실패에는 카드를 숨긴다`() = runTest {
        // Given 오늘 히어로가 (b) 열린 주제 없음 / (c) 오늘 상태 조회 실패 — (a) 반려는 iOS만
        val cases = mapOf(
            TodayPostStatusFailure.NoOpenTopic to HomeHeroState.NoOpenTopic,
            TodayPostStatusFailure.Network to HomeHeroState.Error,
        )
        for ((failure, expectedHero) in cases) {
            val repository = FakeHomeRepository(approved = false).apply {
                todayPostStatus = TodayPostStatusResult.Failure(failure)
            }
            val viewModel = createViewModel(FakeAiReviewRepository(), repository = repository)
            // When  홈을 불러온다
            advanceUntilIdle()
            viewModel.onResume()
            runCurrent()
            // Then  피드백 카드가 숨김
            assertEquals(expectedHero, viewModel.uiState.value.hero)
            assertNull(viewModel.uiState.value.feedbackNotice)
            assertFalse(viewModel.uiState.value.showsFeedback)
        }
    }

    private fun createViewModel(
        ai: FakeAiReviewRepository,
        approved: Boolean = true,
        repository: FakeHomeRepository = FakeHomeRepository(approved),
        session: MutableStateFlow<UserSessionState> = MutableStateFlow(UserSessionState.Authenticated("user")),
        isAiReviewEnabled: () -> Boolean = { true },
    ): HomeViewModel = HomeViewModel(
        repository = repository,
        uploadEntryRepository = repository,
        sessionState = session,
        bannerRepository = repository,
        aiReviewRepository = ai,
        dateProvider = { TODAY },
        isAiReviewEnabled = isAiReviewEnabled,
    ).also { it.startPreloading() }

    private class FakeAiReviewRepository : AiReviewRepository {
        val getPostIds = mutableListOf<String>()
        val requestPostIds = mutableListOf<String>()
        val getResults = ArrayDeque<AiReviewResponseResult>()
        val requestResults = ArrayDeque<AiReviewResponseResult>()
        var getGate: CompletableDeferred<Unit>? = null

        override suspend fun getReview(postId: String): AiReviewResponseResult {
            getPostIds += postId
            getGate?.let { kotlinx.coroutines.withContext(NonCancellable) { it.await() } }
            return getResults.removeFirstOrNull() ?: success(AiReview.Status.PROCESSING)
        }

        override suspend fun requestReview(postId: String): AiReviewResponseResult {
            requestPostIds += postId
            return requestResults.removeFirstOrNull() ?: success(AiReview.Status.PROCESSING)
        }
    }

    private class FakeHomeRepository(var approved: Boolean) :
        PostRepository,
        PhotoUploadEntryRepository,
        BannerRepository {
        override suspend fun getAuthorPosts(
            authorId: String,
            page: Int,
            pageSize: Int,
        ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

        var postId = POST_ID

        // approved가 false일 때 true면 오늘 사진을 검수 대기(PENDING)로 돌려준다.
        var pending = false

        var todayPostStatus: TodayPostStatusResult =
            TodayPostStatusResult.Success(TodayPostStatus(TODAY, false, null, null))

        override suspend fun getPostCalendar(month: YearMonth) = HomeResult.Success(
            PostCalendar(
                month,
                when {
                    approved -> listOf(PostCalendarItem(postId, TODAY, "thumb", PostStatus.APPROVED))
                    pending -> listOf(PostCalendarItem(postId, TODAY, "thumb", PostStatus.PENDING))
                    else -> emptyList()
                },
            ),
        )

        override suspend fun getPostDetail(postId: String) = HomeResult.Success(PostDetail(photo(postId), "하늘", TODAY))

        override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

        override suspend fun updatePost(
            postId: String,
            title: String?,
            description: String?,
            instagramConsent: InstagramConsent,
        ): HomeResult<PostUpdate> = error("unused")

        override suspend fun getPostContent(query: HomeQuery) =
            HomeResult.Success(PostContent(query.date, "하늘", emptyList(), emptySet()))

        override suspend fun getPostPage(query: HomeQuery) =
            HomeResult.Success(PostPage(emptyList(), emptySet(), 1, false, null))

        override suspend fun updateLike(
            photoId: String,
            isLiked: Boolean,
        ): HomeResult<HomeLike> = error("unused")

        override suspend fun getTodayPostStatus() = todayPostStatus

        override suspend fun getBanners(
            page: Int,
            pageSize: Int,
        ) = HomeResult.Success(BannerPage(1, 1, false, emptyList()))
    }

    companion object {
        private const val POST_ID = "post-ai"
        private val TODAY = LocalDate.of(2026, 10, 8)

        private fun success(
            status: AiReview.Status,
            content: AiReviewContent? = null,
            canRequestAgain: Boolean = false,
        ) = AiReviewResponseResult.Success(AiReview(status, null, null, content, canRequestAgain))

        private fun failure(error: AiReviewFailure) = AiReviewResponseResult.Failure(error)

        private fun content() = AiReviewContent(
            overallScore = 4.5,
            criteria = listOf(AiReviewCriterion("composition", "구도", 4.0)),
            distinctivePerspective = true,
            feedback = AiReviewFeedback("summary", "strength", "weakness", "suggestion"),
        )

        private fun photo(id: String) = Post(
            id = id,
            originalImageUrl = "original-$id",
            thumbnailImageUrl = "thumb-$id",
            signatureOriginalImageUrl = "signature",
            signatureThumbnailImageUrl = null,
            contentDescription = "사진",
            title = null,
            likeCount = 0,
            authorId = "author",
        )
    }
}
