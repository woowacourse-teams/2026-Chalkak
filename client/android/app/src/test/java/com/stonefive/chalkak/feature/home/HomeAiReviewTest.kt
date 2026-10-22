package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.domain.model.BannerPage
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.TodayPostStatus
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
    fun `failed review is hidden and never automatically retried`() = runTest {
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
        assertEquals(HomeFeedbackPhase.Hidden, viewModel.uiState.value.feedbackPhase)
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `failed review is hidden whether retry is available or exhausted`() = runTest {
        for (canRequestAgain in listOf(true, false)) {
            val ai = FakeAiReviewRepository().apply {
                getResults += success(AiReview.Status.FAILED, canRequestAgain = canRequestAgain)
            }
            val viewModel = createViewModel(ai)
            advanceUntilIdle()
            viewModel.onResume()
            runCurrent()
            assertEquals(HomeFeedbackPhase.Hidden, viewModel.uiState.value.feedbackPhase)
            assertTrue(ai.requestPostIds.isEmpty())
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

    private fun createViewModel(
        ai: FakeAiReviewRepository,
        approved: Boolean = true,
    ): HomeViewModel {
        val repository = FakeHomeRepository(approved)
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        return HomeViewModel(
            repository = repository,
            uploadEntryRepository = repository,
            sessionState = session,
            bannerRepository = repository,
            aiReviewRepository = ai,
            dateProvider = { TODAY },
        ).also { it.startPreloading() }
    }

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
        override suspend fun getPostCalendar(month: YearMonth) = HomeResult.Success(
            PostCalendar(
                month,
                if (approved) listOf(PostCalendarItem(POST_ID, TODAY, "thumb", PostStatus.APPROVED)) else emptyList(),
            ),
        )

        override suspend fun getPostDetail(postId: String) = HomeResult.Success(PostDetail(photo(postId), "하늘", TODAY))

        override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

        override suspend fun updatePostTitle(
            postId: String,
            title: String?,
        ): HomeResult<PostTitleUpdate> = error("unused")

        override suspend fun getPostContent(query: HomeQuery) =
            HomeResult.Success(PostContent(query.date, "하늘", emptyList(), emptySet()))

        override suspend fun getPostPage(query: HomeQuery) =
            HomeResult.Success(PostPage(emptyList(), emptySet(), 1, false, null))

        override suspend fun updateLike(
            photoId: String,
            isLiked: Boolean,
        ): HomeResult<HomeLike> = error("unused")

        override suspend fun getTodayPostStatus() =
            TodayPostStatusResult.Success(TodayPostStatus(TODAY, false, null, null))

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
        )
    }
}
