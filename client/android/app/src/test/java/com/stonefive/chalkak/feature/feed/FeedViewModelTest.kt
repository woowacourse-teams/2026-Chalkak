package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeHomeRepository
    private lateinit var viewModel: FeedViewModel

    @Before
    fun setUp() {
        repository = FakeHomeRepository()
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
        val selectedRepository = FakeHomeRepository()
        val selectedPost = Post(
            id = "display-photo-1",
            imageUrl = "https://example.com/display-photo.jpg",
            signatureUrl = "https://example.com/display-signature.png",
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
    fun `Display에서 받은 게시물 좋아요는 주입된 Feed repository로만 전달한다`() = runTest {
        val selectedRepository = FakeHomeRepository()
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
        val controlledRepository = ControlledHomeRepository()
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

private class FakeHomeRepository : HomeRepository {
    val requestedQueries = mutableListOf<HomeQuery>()
    var updatedLike: Pair<String, Boolean>? = null
    var failLike = false

    override suspend fun getHome(query: HomeQuery): HomeResult<PostContent> {
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

private class ControlledHomeRepository : HomeRepository {
    private val likeRequests = mutableListOf<CompletableDeferred<HomeResult<HomeLike>>>()

    override suspend fun getHome(query: HomeQuery): HomeResult<PostContent> = HomeResult.Success(feedContent())

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
            imageUrl = "https://example.com/photo.jpg",
            signatureUrl = "https://example.com/signature.png",
            contentDescription = "사진",
            title = "안녕하세요 찰캌입니다.",
            likeCount = 24,
        ),
    ),
    likedPhotoIds = emptySet(),
)
