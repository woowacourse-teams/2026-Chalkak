package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakePostRepository
    private lateinit var viewModel: FeedViewModel

    @Before
    fun setUp() {
        repository = FakePostRepository()
        viewModel = FeedViewModel(repository, dateProvider = { TEST_DATE })
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
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
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
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
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
            initialContent = FeedContentState.Success(
                dateLabel = "8월 5일의 주제",
                topic = "바다",
                post = selectedPost,
                isLiked = false,
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
    fun `Display에서 받은 게시물 좋아요는 주입된 Feed repository로만 전달한다`() = runTest {
        val selectedRepository = FakePostRepository()
        val selectedPost =
            feedContent()
                .photos
                .single()
                .copy(id = "display-mock-photo")
        val selectedViewModel = FeedViewModel(
            repository = selectedRepository,
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
    fun `이전 좋아요 응답이 늦게 도착해도 최신 상태를 유지한다`() = runTest {
        val controlledRepository = ControlledPostRepository()
        val controlledViewModel = FeedViewModel(controlledRepository)

        controlledViewModel.onLikeClicked()
        controlledViewModel.onLikeClicked()
        controlledRepository.completeLike(requestIndex = 1, likeCount = 24)
        controlledRepository.completeLike(requestIndex = 0, likeCount = 25)

        val content = controlledViewModel.uiState.value.content as FeedContentState.Success
        assertFalse(content.isLiked)
        assertEquals(24, content.post.likeCount)
    }
}

private const val PHOTO_ID = "photo-1"
private val TEST_DATE = LocalDate.of(2026, 8, 28)

private class FakePostRepository : PostRepository {
    val requestedQueries = mutableListOf<HomeQuery>()
    var updatedLike: Pair<String, Boolean>? = null
    var failLike = false
    var detailResult: HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)
    var detailRequest: CompletableDeferred<HomeResult<PostDetail>>? = null

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = detailRequest?.await() ?: detailResult

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
        return if (failLike) {
            HomeResult.Failure(HomeFailure.Network)
        } else {
            HomeResult.Success(HomeLike(if (isLiked) 25 else 24, isLiked))
        }
    }
}

private class ControlledPostRepository : PostRepository {
    private val likeRequests = mutableListOf<CompletableDeferred<HomeResult<HomeLike>>>()

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)

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
