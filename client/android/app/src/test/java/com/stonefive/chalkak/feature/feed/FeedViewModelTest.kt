package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostUpdate
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.SignatureHintRepository
import com.stonefive.chalkak.feature.instagramconsent.InstagramConsentInputState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakePostRepository
    private lateinit var viewModel: FeedViewModel

    @Before
    fun setUp() {
        repository = FakePostRepository()
        viewModel =
            FeedViewModel(
                repository,
                aiReviewRepository = FakeAiReviewRepository(),
                sessionState = MutableStateFlow(
                    UserSessionState.SignedOut,
                ),
                dateProvider = {
                    TEST_DATE
                },
            )
    }

    @Test
    fun `화면 진입 시 최신 피드의 첫 번째 게시물을 불러온다`() = runTest {
        val content = viewModel.uiState.value.content as FeedContentState.Success

        assertEquals(
            listOf(
                HomeQuery(
                    date = TEST_DATE,
                    sort = PostSort.LATEST,
                    page = HomeQuery.FIRST_PAGE,
                ),
            ),
            repository.requestedQueries,
        )
        assertEquals("8월 3일의 주제", content.dateLabel)
        assertEquals("하늘하늘하늘", content.topic)
        assertEquals(PHOTO_ID, content.post.id)
        assertFalse(content.isLiked)
    }

    @Test
    fun `전달받은 게시물 정보로 피드 상태를 시작한다`() = runTest {
        val selectedRepository = FakePostRepository()
        val selectedPost = Post(
            id = "display-photo-1",
            originalImageUrl = "https://example.com/display-photo.jpg",
            thumbnailImageUrl = "https://example.com/display-photo-thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/display-signature.png",
            signatureThumbnailImageUrl = "https://example.com/display-signature-thumbnail.png",
            contentDescription = "전시 사진",
            title = "전시에서 선택한 사진",
            likeCount = 31,
            authorId = "author",
        )
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
                topicDate = LocalDate.of(2026, 8, 5),
            ),
        )

        val content = selectedViewModel.uiState.value.content as FeedContentState.Success

        assertEquals(selectedPost, content.post)
        assertEquals("8월 5일의 주제", content.dateLabel)
        assertEquals("바다", content.topic)
        assertTrue(selectedRepository.requestedQueries.isEmpty())
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `기록에서 연 게시물은 상세 조회 후에도 소유권을 유지한다`() = runTest {
        val selectedPost = feedContent()
            .photos
            .single()
            .copy(id = "record-post")
        val selectedRepository = FakePostRepository().apply {
            detailResult = HomeResult.Success(
                PostDetail(
                    post = selectedPost,
                    topic = "바다",
                    topicDate = LocalDate.of(2026, 8, 5),
                ),
            )
        }

        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            postId = selectedPost.id,
            isOwnedByCurrentUser = true,
        )

        advanceUntilIdle()

        assertTrue(
            selectedViewModel.uiState.value.content
                ?.post
                ?.isOwnedByCurrentUser == true,
        )
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `전달받은 캐시를 먼저 보여주고 상세 응답으로 원본 정보를 갱신한다`() = runTest {
        val detailResult = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply {
            detailRequest = detailResult
        }
        val selectedPost = Post(
            id = "display-photo-1",
            originalImageUrl = "https://example.com/original.jpg",
            thumbnailImageUrl = "https://example.com/thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/signature-original.png",
            signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
            contentDescription = "전시 사진",
            title = "목록 제목",
            likeCount = 31,
            authorId = "author",
        )

        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
                topicDate = LocalDate.of(2026, 8, 5),
            ),
            postId = selectedPost.id,
        )

        assertEquals(
            selectedPost,
            selectedViewModel.uiState.value.content
                ?.post,
        )
        assertTrue(selectedViewModel.uiState.value.isRefreshing)

        detailResult.complete(
            HomeResult.Success(
                PostDetail(
                    post = selectedPost.copy(
                        originalImageUrl = "https://example.com/original-updated.jpg",
                        signatureOriginalImageUrl = "https://example.com/signature-original-updated.png",
                        title = "최신 제목",
                        likeCount = 32,
                        isLiked = true,
                        isOwnedByCurrentUser = true,
                    ),
                    topic = "새 바다",
                    topicDate = LocalDate.of(2026, 8, 6),
                ),
            ),
        )
        advanceUntilIdle()

        val updatedContent = selectedViewModel.uiState.value.content
        assertEquals("https://example.com/original-updated.jpg", updatedContent?.post?.originalImageUrl)
        assertEquals("https://example.com/thumbnail.jpg", updatedContent?.post?.thumbnailImageUrl)
        assertEquals("새 바다", updatedContent?.topic)
        assertTrue(updatedContent?.isLiked == true)
        assertTrue(updatedContent?.post?.isOwnedByCurrentUser == true)
        assertFalse(selectedViewModel.uiState.value.isRefreshing)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `상세 응답이 동일하면 기존 게시물 콘텐츠를 유지한다`() = runTest {
        val detailResult = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply {
            detailRequest = detailResult
        }
        val selectedPost = Post(
            id = "display-photo-1",
            originalImageUrl = "https://example.com/original.jpg",
            thumbnailImageUrl = "https://example.com/thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/signature-original.png",
            signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
            contentDescription = "전시 사진",
            title = "목록 제목",
            likeCount = 31,
            authorId = "author",
        )
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
                topicDate = LocalDate.of(2026, 8, 5),
            ),
            postId = selectedPost.id,
        )
        val initialContent = selectedViewModel.uiState.value.content

        detailResult.complete(
            HomeResult.Success(
                PostDetail(
                    post = selectedPost,
                    topic = "바다",
                    topicDate = LocalDate.of(2026, 8, 5),
                ),
            ),
        )
        advanceUntilIdle()

        assertSame(initialContent, selectedViewModel.uiState.value.content)
        assertFalse(selectedViewModel.uiState.value.isRefreshing)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `상세 갱신 실패는 기존 콘텐츠를 유지하고 Toast 메시지를 보낸다`() = runTest {
        val detailResult = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply { detailRequest = detailResult }
        val initial = FeedContentState.Success(
            dateLabel = "8월 5일의 주제",
            topic = "바다",
            post = feedContent().photos.single(),
            isLiked = false,
        )
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = initial,
            postId = initial.post.id,
        )

        detailResult.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()

        assertSame(initial, selectedViewModel.uiState.value.content)
        assertEquals(null, selectedViewModel.uiState.value.errorMessage)
        assertFalse(selectedViewModel.uiState.value.isRefreshing)
        assertEquals(
            "게시물을 불러오지 못했어요",
            (selectedViewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `좋아요 이후 도착한 이전 상세 응답은 최신 좋아요 상태를 덮어쓰지 않는다`() = runTest {
        val detailResult = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply {
            detailRequest = detailResult
            likeResult = HomeResult.Success(HomeLike(likeCount = 32, isLiked = true))
        }
        val selectedPost = Post(
            id = "display-photo-1",
            originalImageUrl = "https://example.com/original.jpg",
            thumbnailImageUrl = "https://example.com/thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/signature-original.png",
            signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
            contentDescription = "전시 사진",
            title = "목록 제목",
            likeCount = 31,
            authorId = "author",
        )
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
            ),
            postId = selectedPost.id,
        )

        selectedViewModel.onLikeClicked()
        detailResult.complete(
            HomeResult.Success(
                PostDetail(
                    post = selectedPost,
                    topic = "바다",
                    topicDate = LocalDate.of(2026, 8, 5),
                ),
            ),
        )
        advanceUntilIdle()

        val updatedContent = selectedViewModel.uiState.value.content
        assertEquals(32, updatedContent?.post?.likeCount)
        assertTrue(updatedContent?.post?.isLiked == true)
        assertTrue(updatedContent?.isLiked == true)
    }

    @Test
    fun `다른 화면에서 변경한 현재 게시물의 좋아요를 등록과 취소 모두 동기화한다`() = runTest {
        repository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 32, isLiked = true))
        advanceUntilIdle()

        val likedContent = viewModel.uiState.value.content
        assertEquals(32, likedContent?.post?.likeCount)
        assertTrue(likedContent?.post?.isLiked == true)
        assertTrue(likedContent?.isLiked == true)

        repository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 31, isLiked = false))
        advanceUntilIdle()

        val unlikedContent = viewModel.uiState.value.content
        assertEquals(31, unlikedContent?.post?.likeCount)
        assertFalse(unlikedContent?.post?.isLiked == true)
        assertFalse(unlikedContent?.isLiked == true)
    }

    @Test
    fun `다른 게시물의 좋아요 변경은 현재 피드를 바꾸지 않는다`() = runTest {
        val initialContent = viewModel.uiState.value.content

        repository.likeUpdates.emit(PostLikeUpdate("other-post", likeCount = 100, isLiked = true))
        advanceUntilIdle()

        assertSame(initialContent, viewModel.uiState.value.content)
    }

    @Test
    fun `첫 상세 조회보다 늦게 도착한 응답은 다른 화면에서 동기화한 좋아요를 덮어쓰지 않는다`() = runTest {
        val detailResult = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply { detailRequest = detailResult }
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            postId = PHOTO_ID,
        )

        selectedRepository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 32, isLiked = true))
        advanceUntilIdle()
        detailResult.complete(
            HomeResult.Success(
                PostDetail(
                    post = feedContent().photos.single(),
                    topic = "바다",
                    topicDate = LocalDate.of(2026, 8, 5),
                ),
            ),
        )
        advanceUntilIdle()

        val updatedContent = selectedViewModel.uiState.value.content
        assertEquals(32, updatedContent?.post?.likeCount)
        assertTrue(updatedContent?.post?.isLiked == true)
        assertTrue(updatedContent?.isLiked == true)
    }

    @Test
    fun `Display에서 받은 게시물 좋아요는 주입된 Feed repository로만 전달한다`() = runTest {
        val selectedRepository = FakePostRepository()
        val selectedPost =
            feedContent()
                .photos
                .single()
                .copy(id = "display-mock-photo")
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
            ),
        )

        selectedViewModel.onLikeClicked()

        assertEquals("display-mock-photo" to true, selectedRepository.updatedLike)
        assertTrue(selectedRepository.requestedQueries.isEmpty())
    }

    @Test
    fun `좋아요 액션은 게시물 상태와 저장소를 함께 갱신한다`() = runTest {
        viewModel.onLikeClicked()

        val content = viewModel.uiState.value.content as FeedContentState.Success
        assertTrue(content.isLiked)
        assertEquals(25, content.post.likeCount)
        assertEquals(PHOTO_ID to true, repository.updatedLike)
    }

    @Test
    fun `좋아요 실패 시 이전 게시물 상태를 복원하고 토스트를 띄운다`() = runTest {
        repository.failLike = true

        viewModel.onLikeClicked()

        val content = viewModel.uiState.value.content as FeedContentState.Success
        assertFalse(content.isLiked)
        assertEquals(24, content.post.likeCount)
        assertEquals("좋아요를 변경하지 못했어요", (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text)
    }

    @Test
    fun `진행 중 좋아요가 실패해도 이후 다른 화면에서 동기화한 상태를 복원하지 않는다`() = runTest {
        val pendingLike = CompletableDeferred<HomeResult<HomeLike>>()
        repository.likeRequest = pendingLike
        viewModel.onLikeClicked()
        repository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 41, isLiked = true))
        advanceUntilIdle()

        pendingLike.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertEquals(41, content?.post?.likeCount)
        assertTrue(content?.post?.isLiked == true)
        assertTrue(content?.isLiked == true)
    }

    @Test
    fun `진행 중 좋아요가 성공해도 이후 다른 화면에서 동기화한 상태를 덮어쓰지 않는다`() = runTest {
        val pendingLike = CompletableDeferred<HomeResult<HomeLike>>()
        repository.likeRequest = pendingLike
        viewModel.onLikeClicked()
        repository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 40, isLiked = false))
        advanceUntilIdle()

        pendingLike.complete(HomeResult.Success(HomeLike(likeCount = 25, isLiked = true)))
        advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertEquals(40, content?.post?.likeCount)
        assertFalse(content?.post?.isLiked == true)
        assertFalse(content?.isLiked == true)
    }

    @Test
    fun `좋아요 성공 반환 전에 같은 결과가 동기화되어도 서버 상태를 유지한다`() = runTest {
        val pendingLike = CompletableDeferred<HomeResult<HomeLike>>()
        repository.likeRequest = pendingLike
        viewModel.onLikeClicked()
        repository.likeUpdates.emit(PostLikeUpdate(PHOTO_ID, likeCount = 32, isLiked = true))
        advanceUntilIdle()

        pendingLike.complete(HomeResult.Success(HomeLike(likeCount = 32, isLiked = true)))
        advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertEquals(32, content?.post?.likeCount)
        assertTrue(content?.post?.isLiked == true)
        assertTrue(content?.isLiked == true)
    }

    @Test
    fun `이전 좋아요 응답이 늦게 도착해도 최신 상태를 유지한다`() = runTest {
        val controlledRepository = ControlledPostRepository()
        val controlledViewModel =
            FeedViewModel(
                controlledRepository,
                aiReviewRepository = FakeAiReviewRepository(),
                sessionState = MutableStateFlow(UserSessionState.SignedOut),
            )

        controlledViewModel.onLikeClicked()
        controlledViewModel.onLikeClicked()
        controlledRepository.completeLike(requestIndex = 1, likeCount = 24)
        controlledRepository.completeLike(requestIndex = 0, likeCount = 25)

        val content = controlledViewModel.uiState.value.content as FeedContentState.Success
        assertFalse(content.isLiked)
        assertEquals(24, content.post.likeCount)
    }

    @Test
    fun `기록에서 연 내 게시물 삭제 성공 시 완료 이벤트를 보낸다`() = runTest {
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        val selectedViewModel = FeedViewModel(
            repository = repository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 3일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
            ),
        )

        selectedViewModel.deletePost()
        advanceUntilIdle()

        assertEquals(PHOTO_ID, repository.deletedPostId)
        assertEquals(PHOTO_ID, selectedViewModel.uiState.value.deleteSuccessPostId)
        assertFalse(selectedViewModel.uiState.value.isDeleting)
    }

    @Test
    fun `다른 사용자 게시물은 삭제 요청을 보내지 않는다`() = runTest {
        viewModel.deletePost()
        advanceUntilIdle()

        assertEquals(null, repository.deletedPostId)
    }

    @Test
    fun `게시물 삭제 권한 실패는 화면을 유지하고 오류를 표시한다`() = runTest {
        repository.deleteResult = HomeResult.Failure(HomeFailure.Http(403))
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        val selectedViewModel = FeedViewModel(
            repository = repository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 3일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
            ),
        )

        selectedViewModel.deletePost()
        advanceUntilIdle()

        assertEquals(
            "본인이 작성한 게시물만 삭제할 수 있어요",
            (selectedViewModel.uiState.value.pendingMessage as UiMessage.Snackbar).text,
        )
        assertFalse(selectedViewModel.uiState.value.isDeleting)
    }

    @Test
    fun `내 게시물 제목 수정 성공 시 화면 제목과 완료 상태를 갱신한다`() = runTest {
        repository.titleUpdateResult = HomeResult.Success(
            PostUpdate(PHOTO_ID, "수정한 제목", null),
        )
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        // 본인 동의 정보는 상세 조회로만 받으므로, 수정하려면 상세 조회가 성공해 있어야 한다(#939).
        repository.detailResult = HomeResult.Success(
            PostDetail(ownedPost, "하늘하늘하늘", TEST_DATE, InstagramConsent.NotAgreed),
        )
        val selectedViewModel = FeedViewModel(
            repository = repository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 3일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
                topicDate = TEST_DATE,
            ),
            dateProvider = { TEST_DATE },
        )

        selectedViewModel.startPostEdit()
        selectedViewModel.updatePostEditTitle("  수정한 제목  ")
        selectedViewModel.savePostEdit()
        advanceUntilIdle()

        assertEquals(PHOTO_ID to "  수정한 제목  ", repository.updatedTitle)
        assertEquals(
            "수정한 제목",
            selectedViewModel.uiState.value.content
                ?.post
                ?.title,
        )
        assertEquals(null, selectedViewModel.uiState.value.postEdit)
        assertFalse(selectedViewModel.uiState.value.isSavingPostEdit)
        assertEquals(
            "게시물을 수정했어요",
            (selectedViewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `지난 주제의 내 게시물은 제목 수정 요청을 보내지 않는다`() = runTest {
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        val selectedViewModel = FeedViewModel(
            repository = repository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 27일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
                topicDate = TEST_DATE.minusDays(1),
            ),
            dateProvider = { TEST_DATE },
        )

        selectedViewModel.startPostEdit()
        selectedViewModel.savePostEdit()
        advanceUntilIdle()

        assertEquals(null, repository.updatedTitle)
        assertFalse(selectedViewModel.uiState.value.isPostEditable)
    }

    @Test
    fun `다른 사용자 게시물은 제목 수정 요청을 보내지 않는다`() = runTest {
        viewModel.startPostEdit()
        viewModel.savePostEdit()
        advanceUntilIdle()

        assertEquals(null, repository.updatedTitle)
    }

    @Test
    fun `상세 새로고침 중에는 제목 수정 요청을 보내지 않는다`() = runTest {
        val detailRequest = CompletableDeferred<HomeResult<PostDetail>>()
        val selectedRepository = FakePostRepository().apply {
            this.detailRequest = detailRequest
        }
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 3일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
            ),
            postId = ownedPost.id,
        )

        assertTrue(selectedViewModel.uiState.value.isRefreshing)
        selectedViewModel.startPostEdit()
        selectedViewModel.savePostEdit()

        assertEquals(null, selectedRepository.updatedTitle)
        assertFalse(selectedViewModel.uiState.value.isSavingPostEdit)

        detailRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun `수정을 시작하면 지금 제목과 설명이 입력칸에 채워진다`() = runTest {
        // Given 제목 "퇴근길 노을", 설명 "회사 앞 육교에서 찍었어요"인 내 게시물
        val editViewModel = ownedPostViewModel(
            title = "퇴근길 노을",
            description = "회사 앞 육교에서 찍었어요",
        )

        // When  수정을 누른다
        editViewModel.startPostEdit()

        // Then  수정 입력의 제목 "퇴근길 노을", 설명 "회사 앞 육교에서 찍었어요"
        assertEquals(
            FeedPostEditState(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요"),
            editViewModel.uiState.value.postEdit,
        )
    }

    @Test
    fun `저장하면 제목과 설명을 한 요청에 보낸다`() = runTest {
        // Given 제목 "퇴근길 노을", 설명 없음인 내 게시물
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = null)
        editViewModel.startPostEdit()

        // When  설명을 "회사 앞 육교에서 찍었어요"로 쓰고 저장한다
        editViewModel.updatePostEditDescription("회사 앞 육교에서 찍었어요")
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  요청이 title "퇴근길 노을", description "회사 앞 육교에서 찍었어요"
        assertEquals(
            Triple(PHOTO_ID, "퇴근길 노을", "회사 앞 육교에서 찍었어요"),
            repository.updatedPost,
        )
    }

    @Test
    fun `제목만 고쳐도 기존 설명을 지우지 않는다`() = runTest {
        // Given 설명 "회사 앞 육교에서 찍었어요"인 내 게시물
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요")
        editViewModel.startPostEdit()

        // When  제목만 "퇴근길"로 바꾸고 저장한다
        editViewModel.updatePostEditTitle("퇴근길")
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  요청의 description "회사 앞 육교에서 찍었어요"
        assertEquals(
            Triple(PHOTO_ID, "퇴근길", "회사 앞 육교에서 찍었어요"),
            repository.updatedPost,
        )
    }

    @Test
    fun `저장에 성공하면 상세의 제목·설명이 응답 값으로 바뀐다`() = runTest {
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요")
        editViewModel.startPostEdit()

        // Given 저장 응답이 title "퇴근길", description "회사 앞 육교에서\n찍었어요"
        repository.titleUpdateResult = HomeResult.Success(
            PostUpdate(PHOTO_ID, "퇴근길", "회사 앞 육교에서\n찍었어요"),
        )

        // When  저장이 끝난다
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  상세 제목 "퇴근길", 설명 "회사 앞 육교에서\n찍었어요", 수정 화면 닫힘, 안내 "게시물을 수정했어요"
        val state = editViewModel.uiState.value
        assertEquals(
            "퇴근길",
            state.content
                ?.post
                ?.title,
        )
        assertEquals(
            "회사 앞 육교에서\n찍었어요",
            state.content
                ?.post
                ?.description,
        )
        assertEquals(null, state.postEdit)
        assertEquals("게시물을 수정했어요", (state.pendingMessage as UiMessage.Toast).text)
    }

    @Test
    fun `저장 중에 다시 저장해도 요청은 한 번만 간다`() = runTest {
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요")
        editViewModel.startPostEdit()

        // Given 저장 요청이 아직 끝나지 않음
        val pendingRequest = CompletableDeferred<HomeResult<PostUpdate>>()
        repository.updatePostRequest = pendingRequest
        editViewModel.savePostEdit()
        runCurrent()

        // When  저장을 한 번 더 누른다
        editViewModel.savePostEdit()
        runCurrent()

        // Then  수정 요청 횟수 1
        assertEquals(1, repository.updatePostCalls)

        pendingRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
    }

    @Test
    fun `저장이 실패하면 입력이 남고 상세 값은 그대로다`() = runTest {
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요")
        editViewModel.startPostEdit()

        // Given 수정 요청이 서버 오류(500)로 실패한다
        repository.titleUpdateResult = HomeResult.Failure(HomeFailure.Http(500))

        // When  설명을 "새 설명"으로 바꾸고 저장한다
        editViewModel.updatePostEditDescription("새 설명")
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  수정 화면이 열린 채 설명 입력 "새 설명", 상세 설명 "회사 앞 육교에서 찍었어요", 실패 안내 표시
        val state = editViewModel.uiState.value
        assertEquals("새 설명", state.postEdit?.description)
        assertEquals(
            "회사 앞 육교에서 찍었어요",
            state.content
                ?.post
                ?.description,
        )
        assertEquals("게시물을 수정하지 못했어요", (state.pendingMessage as UiMessage.Toast).text)
    }

    @Test
    fun `수정을 닫으면 저장하지 않고 입력이 사라진다`() = runTest {
        // Given 설명을 "새 설명"으로 고친 수정 입력
        val editViewModel = ownedPostViewModel(title = "퇴근길 노을", description = "회사 앞 육교에서 찍었어요")
        editViewModel.startPostEdit()
        editViewModel.updatePostEditDescription("새 설명")

        // When  닫기(뒤로)를 누른다
        editViewModel.cancelPostEdit()
        advanceUntilIdle()

        // Then  수정 입력이 없고, 요청 횟수 0, 상세 설명 "회사 앞 육교에서 찍었어요"
        val state = editViewModel.uiState.value
        assertEquals(null, state.postEdit)
        assertEquals(0, repository.updatePostCalls)
        assertEquals(
            "회사 앞 육교에서 찍었어요",
            state.content
                ?.post
                ?.description,
        )
    }

    @Test
    fun `수정을 시작하면 현재 동의와 ID가 채워진다`() = runTest {
        // Given 본인 게시물 상세의 동의 true, ID "chalkak.daily"
        val editViewModel = ownedPostViewModel(
            title = "퇴근길 노을",
            description = null,
            instagramConsent = InstagramConsent(agreed = true, instagramId = "chalkak.daily"),
        )

        // When  수정
        editViewModel.startPostEdit()

        // Then  수정 상태의 동의가 켜짐, ID가 "chalkak.daily"
        assertEquals(
            InstagramConsentInputState(agreed = true, instagramId = "chalkak.daily"),
            editViewModel.uiState.value.postEdit
                ?.instagramConsent,
        )
    }

    @Test
    fun `수정에서 동의를 끄고 저장하면 제목·설명과 함께 철회를 보낸다`() = runTest {
        // Given 제목 "퇴근길 노을", 설명 "육교에서", 동의 true, ID "chalkak.daily"인 본인 게시물의 수정 상태
        val editViewModel = ownedPostViewModel(
            title = "퇴근길 노을",
            description = "육교에서",
            instagramConsent = InstagramConsent(agreed = true, instagramId = "chalkak.daily"),
        )
        editViewModel.startPostEdit()
        assertEquals(
            true,
            editViewModel.uiState.value.postEdit
                ?.instagramConsent
                ?.agreed,
        )

        // When  스위치를 끄고 저장
        editViewModel.updatePostEditInstagramUploadAgreed(false)
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  수정 요청이 title "퇴근길 노을", description "육교에서", instagramUploadAgreed false, instagramId null
        assertEquals(Triple(PHOTO_ID, "퇴근길 노을", "육교에서"), repository.updatedPost)
        assertEquals(InstagramConsent(agreed = false, instagramId = null), repository.updatedConsent)
    }

    @Test
    fun `제목만 고쳐도 동의와 ID를 지우지 않는다`() = runTest {
        // Given 동의 true, ID "chalkak.daily"인 본인 게시물의 수정 상태
        val editViewModel = ownedPostViewModel(
            title = "퇴근길 노을",
            description = null,
            instagramConsent = InstagramConsent(agreed = true, instagramId = "chalkak.daily"),
        )
        editViewModel.startPostEdit()

        // When  제목만 "새 제목"으로 바꾸고 저장
        editViewModel.updatePostEditTitle("새 제목")
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  수정 요청의 instagramUploadAgreed가 true, instagramId가 "chalkak.daily"
        assertEquals(InstagramConsent(agreed = true, instagramId = "chalkak.daily"), repository.updatedConsent)
    }

    @Test
    fun `수정 저장에서 서버가 ID 형식을 거절하면 시트에 오류를 보여 준다`() = runTest {
        // Given 수정 상태에서 동의 켜짐, ID "chalkak-daily", 수정 요청이 400 "인스타그램 ID 형식이 올바르지 않습니다."
        val savedConsent = InstagramConsent(agreed = true, instagramId = "chalkak.daily")
        val editViewModel = ownedPostViewModel(
            title = "퇴근길 노을",
            description = null,
            instagramConsent = savedConsent,
        )
        editViewModel.startPostEdit()
        editViewModel.updatePostEditInstagramId("chalkak-daily")
        repository.titleUpdateResult = HomeResult.Failure(HomeFailure.InvalidInstagramId)

        // When  저장
        editViewModel.savePostEdit()
        advanceUntilIdle()

        // Then  수정 시트가 열린 채 ID 오류가 "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요", 상세의 동의·ID는 그대로
        val state = editViewModel.uiState.value
        assertEquals(
            InstagramConsentInputState(
                agreed = true,
                instagramId = "chalkak-daily",
                instagramIdError = "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요",
            ),
            state.postEdit?.instagramConsent,
        )
        assertEquals(savedConsent, state.instagramConsent)
        assertFalse(state.isSavingPostEdit)
    }

    @Test
    fun `본인 동의 정보를 못 불러왔으면 수정을 열지 않는다`() = runTest {
        // Given 피드 목록에서 들어와 상세 조회가 네트워크 오류로 끝난 본인 게시물(동의 정보 없음)
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(isOwnedByCurrentUser = true)
        repository.detailResult = HomeResult.Failure(HomeFailure.Network)
        val editViewModel = FeedViewModel(
            repository = repository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 28일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
                topicDate = TEST_DATE,
            ),
            postId = ownedPost.id,
            dateProvider = { TEST_DATE },
        )
        advanceUntilIdle()

        // When  수정
        editViewModel.startPostEdit()

        // Then  수정 상태가 열리지 않고 안내 "게시물을 불러오지 못해 수정할 수 없어요"
        val state = editViewModel.uiState.value
        assertEquals(null, state.postEdit)
        assertEquals("게시물을 불러오지 못해 수정할 수 없어요", (state.pendingMessage as UiMessage.Toast).text)
    }

    /**
     * 오늘 주제에 올린 내 게시물 상세를 연 ViewModel. 수정 TC들이 같은 시작 상태를 쓴다.
     * 본인 동의 정보는 상세 조회로만 받으므로 상세 조회가 [instagramConsent]와 함께 성공한 상태로 만든다(#939).
     */
    private fun ownedPostViewModel(
        title: String?,
        description: String?,
        postRepository: FakePostRepository = repository,
        instagramConsent: InstagramConsent = InstagramConsent.NotAgreed,
    ): FeedViewModel {
        val ownedPost = feedContent()
            .photos
            .single()
            .copy(
                title = title,
                description = description,
                isOwnedByCurrentUser = true,
            )
        postRepository.detailResult = HomeResult.Success(
            PostDetail(ownedPost, "하늘하늘하늘", TEST_DATE, instagramConsent),
        )
        return FeedViewModel(
            repository = postRepository,
            aiReviewRepository = FakeAiReviewRepository(),
            sessionState = MutableStateFlow(UserSessionState.SignedOut),
            initialContent = FeedContentState.Success(
                dateLabel = "8월 28일의 주제",
                topic = "하늘하늘하늘",
                post = ownedPost,
                isLiked = false,
                topicDate = TEST_DATE,
            ),
            dateProvider = { TEST_DATE },
        )
    }

    @Test
    fun `비로그인 사인 탭은 로그인 안내만 표시한다`() = runTest {
        val selected = signatureViewModel(UserSessionState.Guest)
        assertEquals(null, selected.onSignatureClicked())
        assertEquals(
            "다른 사진도 보려면 로그인이 필요해요",
            (selected.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `로그인 사인 탭은 게시물 ID 대신 작가 ID를 반환한다`() = runTest {
        val selected = signatureViewModel(UserSessionState.Authenticated("viewer"))
        assertEquals("author", selected.onSignatureClicked()?.authorId)
    }

    @Test
    fun `사인이 보이면 안내 시트를 띄우고 시간이 지나도 닫지 않는다`() = runTest {
        val selected = signatureViewModel()
        selected.onSignatureVisible()
        runCurrent()
        assertTrue(selected.uiState.value.showSignatureHint)

        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(selected.uiState.value.showSignatureHint)
    }

    @Test
    fun `사인을 눌러 작가 사진으로 이동하면 안내를 닫고 다음 진입부터 띄우지 않는다`() = runTest {
        val hint = FakeSignatureHintRepository()
        val selected = signatureViewModel(hint = hint)
        selected.onSignatureVisible()
        runCurrent()

        assertEquals("author", selected.onSignatureClicked()?.authorId)
        runCurrent()

        assertFalse(selected.uiState.value.showSignatureHint)
        assertEquals(1, hint.completeCalls)
        val reopened = signatureViewModel(hint = hint)
        reopened.onSignatureVisible()
        runCurrent()
        assertFalse(reopened.uiState.value.showSignatureHint)
    }

    @Test
    fun `안내 시트를 닫으면 완료로 기록하고 다음 진입부터 띄우지 않는다`() = runTest {
        val hint = FakeSignatureHintRepository()
        val selected = signatureViewModel(hint = hint)
        selected.onSignatureVisible()
        runCurrent()

        selected.dismissSignatureHint()
        runCurrent()

        assertFalse(selected.uiState.value.showSignatureHint)
        assertEquals(1, hint.completeCalls)
        val reopened = signatureViewModel(hint = hint)
        reopened.onSignatureVisible()
        runCurrent()
        assertFalse(reopened.uiState.value.showSignatureHint)
    }

    @Test
    fun `안내 시트를 닫지 않은 채 다시 들어오면 안내를 다시 띄운다`() = runTest {
        val hint = FakeSignatureHintRepository()
        signatureViewModel(hint = hint).onSignatureVisible()
        runCurrent()

        val reopened = signatureViewModel(hint = hint)
        reopened.onSignatureVisible()
        runCurrent()

        assertTrue(reopened.uiState.value.showSignatureHint)
        assertEquals(0, hint.completeCalls)
    }

    @Test
    fun `내 사진이어도 처음에는 안내를 띄운다`() = runTest {
        val mine = signatureViewModel(isMine = true)
        mine.onSignatureVisible()
        runCurrent()
        assertTrue(mine.uiState.value.showSignatureHint)
    }

    @Test
    fun `비로그인 사인 탭은 안내를 닫지만 완료로 기록하지 않는다`() = runTest {
        val hint = FakeSignatureHintRepository()
        val selected = signatureViewModel(session = UserSessionState.SignedOut, hint = hint)
        selected.onSignatureVisible()
        runCurrent()
        assertTrue(selected.uiState.value.showSignatureHint)

        assertEquals(null, selected.onSignatureClicked())
        runCurrent()

        assertFalse(selected.uiState.value.showSignatureHint)
        assertEquals(0, hint.completeCalls)
    }

    private fun signatureViewModel(
        session: UserSessionState = UserSessionState.Authenticated("viewer"),
        hint: SignatureHintRepository = FakeSignatureHintRepository(),
        isMine: Boolean = false,
    ): FeedViewModel = FeedViewModel(
        repository = FakePostRepository(),
        aiReviewRepository = FakeAiReviewRepository(),
        sessionState = MutableStateFlow(session),
        initialContent = FeedContentState.Success(
            dateLabel = "오늘의 주제",
            topic = "하늘",
            post = feedContent()
                .photos
                .single()
                .copy(authorId = "author", isOwnedByCurrentUser = isMine),
            isLiked = false,
        ),
        postId = null,
        signatureHintRepository = hint,
    )
}

private class FakeSignatureHintRepository : SignatureHintRepository {
    var completeCalls = 0
    private var completed = false

    override suspend fun isCompleted(): Boolean = completed

    override suspend fun complete() {
        completeCalls++
        completed = true
    }
}

private const val PHOTO_ID = "photo-1"
private val TEST_DATE = LocalDate.of(2026, 8, 28)

private class FakePostRepository : PostRepository {
    override val likeUpdates = MutableSharedFlow<PostLikeUpdate>(extraBufferCapacity = 16)

    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

    val requestedQueries = mutableListOf<HomeQuery>()
    var updatedLike: Pair<String, Boolean>? = null
    var failLike = false
    var likeResult: HomeResult<HomeLike>? = null
    var likeRequest: CompletableDeferred<HomeResult<HomeLike>>? = null
    var detailResult: HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)
    var detailRequest: CompletableDeferred<HomeResult<PostDetail>>? = null
    var deleteResult: HomeResult<Unit> = HomeResult.Success(Unit)
    var deletedPostId: String? = null
    var titleUpdateResult: HomeResult<PostUpdate> = HomeResult.Success(
        PostUpdate(PHOTO_ID, "수정한 제목", null),
    )
    var updatedTitle: Pair<String, String?>? = null
    var updatedPost: Triple<String, String?, String?>? = null
    var updatedConsent: InstagramConsent? = null
    var updatePostCalls = 0
    var updatePostRequest: CompletableDeferred<HomeResult<PostUpdate>>? = null

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = detailRequest?.await() ?: detailResult

    override suspend fun deletePost(postId: String): HomeResult<Unit> {
        deletedPostId = postId
        return deleteResult
    }

    override suspend fun updatePost(
        postId: String,
        title: String?,
        description: String?,
        instagramConsent: InstagramConsent,
    ): HomeResult<PostUpdate> {
        updatePostCalls++
        updatedTitle = postId to title
        updatedPost = Triple(postId, title, description)
        updatedConsent = instagramConsent
        return updatePostRequest?.await() ?: titleUpdateResult
    }

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        requestedQueries += query
        return HomeResult.Success(feedContent())
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> {
        updatedLike = photoId to isLiked
        return likeRequest?.await() ?: likeResult ?: if (failLike) {
            HomeResult.Failure(HomeFailure.Network)
        } else {
            HomeResult.Success(HomeLike(if (isLiked) 25 else 24, isLiked))
        }
    }
}

private class FakeAiReviewRepository : AiReviewRepository {
    override suspend fun getReview(postId: String): AiReviewResponseResult =
        AiReviewResponseResult.Success(AiReview(AiReview.Status.NONE, null, null, null, false))

    override suspend fun requestReview(postId: String): AiReviewResponseResult =
        AiReviewResponseResult.Success(AiReview(AiReview.Status.PROCESSING, null, null, null, false))
}

private class ControlledPostRepository : PostRepository {
    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): HomeResult<AuthorPostsPage> = HomeResult.Failure(HomeFailure.Network)

    private val likeRequests = mutableListOf<CompletableDeferred<HomeResult<HomeLike>>>()

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)

    override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

    override suspend fun updatePost(
        postId: String,
        title: String?,
        description: String?,
        instagramConsent: InstagramConsent,
    ): HomeResult<PostUpdate> = error("unused")

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> = HomeResult.Success(feedContent())

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> {
        val request = CompletableDeferred<HomeResult<HomeLike>>()
        likeRequests += request
        return request.await()
    }

    fun completeLike(
        requestIndex: Int,
        likeCount: Int,
    ) {
        likeRequests[requestIndex].complete(
            HomeResult.Success(
                HomeLike(
                    likeCount = likeCount,
                    isLiked = likeCount > 24,
                ),
            ),
        )
    }
}

private fun feedContent() = PostContent(
    topicDate = LocalDate.of(2026, 8, 3),
    topic = "하늘하늘하늘",
    photos = listOf(
        Post(
            id = PHOTO_ID,
            originalImageUrl = "https://example.com/photo.jpg",
            thumbnailImageUrl = "https://example.com/photo-thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/signature.png",
            signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
            contentDescription = "사진",
            title = "안녕하세요 찰캌입니다.",
            likeCount = 24,
            authorId = "author",
        ),
    ),
    likedPhotoIds = emptySet(),
)
