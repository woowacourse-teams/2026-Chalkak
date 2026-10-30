package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.feature.aireview.AiReviewPhase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedAiReviewTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `owned initial content loads review without explicit post id`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        assertEquals(listOf(PHOTO_ID), ai.getPostIds)
        assertEquals(AiReviewPhase.Ready, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `remote config disabled hides review and never queries or starts it`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(
            ai,
            initialContent = feedContent(isOwned = true),
            isAiReviewEnabled = { false },
        )
        viewModel.onResume()
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.getPostIds.isEmpty())
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `remote config turned off while away hides a shown review on return`() = runTest {
        var enabled = true
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(
            ai,
            initialContent = feedContent(isOwned = true),
            isAiReviewEnabled = { enabled },
        )
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Ready, viewModel.uiState.value.aiReview.phase)

        viewModel.onPause()
        enabled = false
        viewModel.onResume()
        runCurrent()
        assertEquals(listOf(PHOTO_ID), ai.getPostIds)
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `foreign content does not query or start review`() = runTest {
        val ai = FeedAiReviewFake()
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = false))
        viewModel.onResume()
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.getPostIds.isEmpty())
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `duplicate start clicks produce one POST`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += success(AiReview.Status.PROCESSING)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(listOf(PHOTO_ID), ai.requestPostIds)
    }

    @Test
    fun `processing polls every three seconds and completes`() = runTest {
        val result = reviewContent()
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, result)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(1, ai.getPostIds.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(AiReviewPhase.Completed, viewModel.uiState.value.aiReview.phase)
        assertEquals(result, viewModel.uiState.value.aiReview.result)
    }

    @Test
    fun `pause cancels polling and resume starts a fresh review GET`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, reviewContent())
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.onPause()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, ai.getPostIds.size)
        viewModel.onResume()
        runCurrent()
        assertEquals(2, ai.getPostIds.size)
        assertEquals(AiReviewPhase.Completed, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `start while resume GET is active cannot submit a duplicate POST`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.NONE)
            requestResults += success(AiReview.Status.PROCESSING)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.onPause()
        ai.getGate = CompletableDeferred()
        viewModel.onResume()
        runCurrent()

        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.requestPostIds.isEmpty())

        ai.getGate!!.complete(Unit)
        runCurrent()
    }

    @Test
    fun `five minute timeout keeps processing`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.PROCESSING) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(AiReviewPhase.Processing, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `failed review shows failure and is never automatically retried`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.FAILED, canRequestAgain = true) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Failed, viewModel.uiState.value.aiReview.phase)
        assertEquals(AiReview.Status.FAILED, viewModel.uiState.value.aiReview.status)
        assertTrue(viewModel.uiState.value.aiReview.canRequestAgain)
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `failed review with retry left can be requested again`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.FAILED, canRequestAgain = true)
            requestResults += success(AiReview.Status.PROCESSING)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(listOf(PHOTO_ID), ai.requestPostIds)
        assertEquals(AiReviewPhase.Processing, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `failed review without retry left cannot be requested again`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.FAILED, canRequestAgain = false) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(AiReviewPhase.Failed, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `rejected request again returns to failure with server message`() = runTest {
        val message = "더 이상 AI 리뷰를 다시 요청할 수 없습니다."
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.FAILED, canRequestAgain = true)
            requestResults += failure(AiReviewFailure.Http(400, "BUSINESS_ERROR", message))
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(AiReviewPhase.Failed, viewModel.uiState.value.aiReview.phase)
        assertEquals(message, viewModel.uiState.value.aiReview.message)
    }

    @Test
    fun `past photo hides failed review`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.FAILED, canRequestAgain = true) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true, topicDate = YESTERDAY))
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
        assertEquals(AiReview.Status.FAILED, viewModel.uiState.value.aiReview.status)
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `POST 400 exposes server message`() = runTest {
        val message = "지금은 AI 리뷰를 요청할 수 없습니다."
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += failure(AiReviewFailure.Http(400, "BUSINESS_ERROR", message))
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(message, viewModel.uiState.value.aiReview.message)
        assertEquals(AiReviewPhase.Ready, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `POST 404 hides review and cancels it`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.NONE)
            requestResults += failure(AiReviewFailure.Http(404, "NOT_FOUND", "없음"))
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        viewModel.startFeedback()
        runCurrent()
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `late response after account change cannot repopulate state`() = runTest {
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))
        val ai = FeedAiReviewFake().apply {
            getGate = CompletableDeferred()
            getResults += success(AiReview.Status.COMPLETED, reviewContent())
        }
        val viewModel = createViewModel(ai, session = session, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        session.value = UserSessionState.Authenticated("other")
        runCurrent()
        ai.getGate!!.complete(Unit)
        runCurrent()
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
        assertNull(viewModel.uiState.value.aiReview.result)
    }

    @Test
    fun `like update does not restart active review polling`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.PROCESSING) }
        val posts = FeedAiPostRepository()
        val viewModel = createViewModel(ai, repository = posts, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        val calls = ai.getPostIds.size
        advanceTimeBy(2_999)
        runCurrent()
        viewModel.onLikeClicked()
        runCurrent()
        assertEquals(calls, ai.getPostIds.size)
    }

    @Test
    fun `network failure while processing notifies once and keeps polling`() = runTest {
        val result = reviewContent()
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.PROCESSING)
            getResults += failure(AiReviewFailure.Network)
            getResults += failure(AiReviewFailure.Network)
            getResults += success(AiReview.Status.COMPLETED, result)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(AiReviewPhase.Processing, viewModel.uiState.value.aiReview.phase)
        assertEquals("네트워크 연결을 확인해 주세요.", viewModel.uiState.value.aiReview.message)
        viewModel.dismissFeedbackMessage()
        advanceTimeBy(3_000)
        runCurrent()
        assertNull(viewModel.uiState.value.aiReview.message)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(4, ai.getPostIds.size)
        assertEquals(AiReviewPhase.Completed, viewModel.uiState.value.aiReview.phase)
        assertEquals(result, viewModel.uiState.value.aiReview.result)
    }

    @Test
    fun `pause clears a failure message so it is not shown again on return`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += failure(AiReviewFailure.Network)
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true))
        viewModel.onResume()
        runCurrent()
        assertEquals("네트워크 연결을 확인해 주세요.", viewModel.uiState.value.aiReview.message)
        viewModel.onPause()
        assertNull(viewModel.uiState.value.aiReview.message)
    }

    @Test
    fun `past photo without review hides start and never sends POST`() = runTest {
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true, topicDate = YESTERDAY))
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.requestPostIds.isEmpty())
    }

    @Test
    fun `past photo keeps showing an existing result`() = runTest {
        val result = reviewContent()
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.COMPLETED, result) }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true, topicDate = YESTERDAY))
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Completed, viewModel.uiState.value.aiReview.phase)
        assertEquals(result, viewModel.uiState.value.aiReview.result)
    }

    @Test
    fun `past photo keeps polling a review that is already processing`() = runTest {
        val ai = FeedAiReviewFake().apply {
            getResults += success(AiReview.Status.PROCESSING)
            getResults += success(AiReview.Status.COMPLETED, reviewContent())
        }
        val viewModel = createViewModel(ai, initialContent = feedContent(isOwned = true, topicDate = YESTERDAY))
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Processing, viewModel.uiState.value.aiReview.phase)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(AiReviewPhase.Completed, viewModel.uiState.value.aiReview.phase)
    }

    @Test
    fun `start after midnight hides the stale button without POST`() = runTest {
        var today = TEST_DATE
        val ai = FeedAiReviewFake().apply { getResults += success(AiReview.Status.NONE) }
        val viewModel = createViewModel(
            ai,
            initialContent = feedContent(isOwned = true),
            dateProvider = { today },
        )
        viewModel.onResume()
        runCurrent()
        assertEquals(AiReviewPhase.Ready, viewModel.uiState.value.aiReview.phase)
        today = TEST_DATE.plusDays(1)
        viewModel.startFeedback()
        runCurrent()
        assertTrue(ai.requestPostIds.isEmpty())
        assertEquals(AiReviewPhase.Hidden, viewModel.uiState.value.aiReview.phase)
    }

    private fun createViewModel(
        ai: FeedAiReviewFake,
        repository: FeedAiPostRepository = FeedAiPostRepository(),
        session: MutableStateFlow<UserSessionState> = MutableStateFlow(UserSessionState.Authenticated("user")),
        initialContent: FeedContentState.Success? = null,
        dateProvider: () -> LocalDate = { TEST_DATE },
        isAiReviewEnabled: () -> Boolean = { true },
    ) = FeedViewModel(
        repository = repository,
        aiReviewRepository = ai,
        sessionState = session,
        initialContent = initialContent,
        dateProvider = dateProvider,
        isAiReviewEnabled = isAiReviewEnabled,
    )

    private class FeedAiReviewFake : AiReviewRepository {
        val getPostIds = mutableListOf<String>()
        val requestPostIds = mutableListOf<String>()
        val getResults = ArrayDeque<AiReviewResponseResult>()
        val requestResults = ArrayDeque<AiReviewResponseResult>()
        var getGate: CompletableDeferred<Unit>? = null

        override suspend fun getReview(postId: String): AiReviewResponseResult {
            getPostIds += postId
            getGate?.let { withContext(NonCancellable) { it.await() } }
            return getResults.removeFirstOrNull() ?: success(AiReview.Status.PROCESSING)
        }

        override suspend fun requestReview(postId: String): AiReviewResponseResult {
            requestPostIds += postId
            return requestResults.removeFirstOrNull() ?: success(AiReview.Status.PROCESSING)
        }
    }

    private class FeedAiPostRepository : PostRepository {
        override suspend fun getAuthorPosts(
            authorId: String,
            page: Int,
            pageSize: Int,
        ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

        override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")
        override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")
        override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")
        override suspend fun updatePostTitle(
            postId: String,
            title: String?,
        ): HomeResult<PostTitleUpdate> = error("unused")
        override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> = error("unused")
        override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")
        override suspend fun updateLike(
            photoId: String,
            isLiked: Boolean,
        ): HomeResult<HomeLike> = HomeResult.Success(HomeLike(1, isLiked))
    }

    companion object {
        private const val PHOTO_ID = "photo-ai"
        private val TEST_DATE = LocalDate.of(2026, 8, 28)
        private val YESTERDAY = TEST_DATE.minusDays(1)

        private fun feedContent(
            isOwned: Boolean,
            topicDate: LocalDate = TEST_DATE,
        ) = FeedContentState.Success(
            dateLabel = "8월 28일의 주제",
            topic = "하늘",
            post = Post(
                id = PHOTO_ID,
                originalImageUrl = "original",
                thumbnailImageUrl = "thumbnail",
                signatureOriginalImageUrl = "signature",
                contentDescription = "사진",
                title = null,
                likeCount = 0,
                isOwnedByCurrentUser = isOwned,
                authorId = "author",
            ),
            isLiked = false,
            topicDate = topicDate,
        )

        private fun success(
            status: AiReview.Status,
            result: AiReviewContent? = null,
            canRequestAgain: Boolean = false,
        ) = AiReviewResponseResult.Success(AiReview(status, null, null, result, canRequestAgain))

        private fun failure(error: AiReviewFailure) = AiReviewResponseResult.Failure(error)

        private fun reviewContent() = AiReviewContent(
            overallScore = 4.0,
            criteria = listOf(AiReviewCriterion("composition", "구도", 4.0)),
            distinctivePerspective = false,
            feedback = AiReviewFeedback("요약", "강점", "약점", "제안"),
        )
    }
}
