package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.SignatureHintRepository
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
    fun `좋아요 실패 시 이전 게시물 상태를 복원한다`() = runTest {
        repository.failLike = true

        viewModel.onLikeClicked()

        val content = viewModel.uiState.value.content as FeedContentState.Success
        assertFalse(content.isLiked)
        assertEquals(24, content.post.likeCount)
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
            PostTitleUpdate(PHOTO_ID, "수정한 제목"),
        )
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
                topicDate = TEST_DATE,
            ),
            dateProvider = { TEST_DATE },
        )

        selectedViewModel.updatePostTitle("  수정한 제목  ")
        advanceUntilIdle()

        assertEquals(PHOTO_ID to "  수정한 제목  ", repository.updatedTitle)
        assertEquals(
            "수정한 제목",
            selectedViewModel.uiState.value.content
                ?.post
                ?.title,
        )
        assertEquals(PHOTO_ID, selectedViewModel.uiState.value.titleUpdateSuccessPostId)
        assertFalse(selectedViewModel.uiState.value.isUpdatingTitle)
        assertEquals(
            "제목을 수정했어요",
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

        selectedViewModel.updatePostTitle("수정 시도")
        advanceUntilIdle()

        assertEquals(null, repository.updatedTitle)
        assertFalse(selectedViewModel.uiState.value.isTitleEditable)
    }

    @Test
    fun `다른 사용자 게시물은 제목 수정 요청을 보내지 않는다`() = runTest {
        viewModel.updatePostTitle("수정 시도")
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
        selectedViewModel.updatePostTitle("수정 시도")

        assertEquals(null, selectedRepository.updatedTitle)
        assertFalse(selectedViewModel.uiState.value.isUpdatingTitle)

        detailRequest.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()
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
    fun `다른 작가 사인이 보이면 노출을 기록하고 4초 후 닫는다`() = runTest {
        val hint = FakeSignatureHintRepository()
        val selected = signatureViewModel(hint = hint)
        selected.onSignatureVisible()
        runCurrent()
        assertTrue(selected.uiState.value.showSignatureHint)
        assertEquals(1, hint.claims)
        selected.onSignatureVisible()
        assertEquals(1, hint.claims)
        advanceTimeBy(4_000)
        runCurrent()
        assertFalse(selected.uiState.value.showSignatureHint)
    }

    @Test
    fun `내 사인과 이미 노출된 안내는 다시 표시하지 않는다`() = runTest {
        val hint = FakeSignatureHintRepository()
        val mine = signatureViewModel(hint = hint, isMine = true)
        mine.onSignatureVisible()
        runCurrent()
        assertEquals(0, hint.claims)
        val selected = signatureViewModel(hint = hint)
        selected.onSignatureVisible()
        runCurrent()
        selected.dismissSignatureHint()
        val reopened = signatureViewModel(hint = hint)
        reopened.onSignatureVisible()
        runCurrent()
        assertFalse(reopened.uiState.value.showSignatureHint)
    }

    @Test
    fun `사인이 보이기 전 스크롤은 첫 안내를 소진하지 않는다`() = runTest {
        val selected = signatureViewModel()
        selected.dismissSignatureHint()
        selected.onSignatureVisible()
        runCurrent()
        assertTrue(selected.uiState.value.showSignatureHint)
        selected.dismissSignatureHint()
        assertFalse(selected.uiState.value.showSignatureHint)
        selected.onSignatureVisible()
        runCurrent()
        assertFalse(selected.uiState.value.showSignatureHint)
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
    var claims = 0
    private var seen = false

    override suspend fun claimFirstExposure(): Boolean {
        claims++
        if (seen) return false
        seen = true
        return true
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
    var titleUpdateResult: HomeResult<PostTitleUpdate> = HomeResult.Success(
        PostTitleUpdate(PHOTO_ID, "수정한 제목"),
    )
    var updatedTitle: Pair<String, String?>? = null

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = detailRequest?.await() ?: detailResult

    override suspend fun deletePost(postId: String): HomeResult<Unit> {
        deletedPostId = postId
        return deleteResult
    }

    override suspend fun updatePostTitle(
        postId: String,
        title: String?,
    ): HomeResult<PostTitleUpdate> {
        updatedTitle = postId to title
        return titleUpdateResult
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

    override suspend fun updatePostTitle(
        postId: String,
        title: String?,
    ): HomeResult<PostTitleUpdate> = error("unused")

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
        ),
    ),
    likedPhotoIds = emptySet(),
)
