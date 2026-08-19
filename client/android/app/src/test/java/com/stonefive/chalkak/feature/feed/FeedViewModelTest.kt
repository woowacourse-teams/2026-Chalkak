package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
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
        viewModel = FeedViewModel(repository)
    }

    @Test
    fun `화면 진입 시 최신 피드의 첫 번째 게시물을 불러온다`() = runTest {
        val content = viewModel.uiState.value.content as FeedContentState.Success

        assertEquals(listOf(PostSort.LATEST), repository.requestedSorts)
        assertEquals("8월 3일의 주제", content.dateLabel)
        assertEquals("하늘하늘하늘", content.topic)
        assertEquals(PHOTO_ID, content.post.id)
        assertFalse(content.isLiked)
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

private class FakeHomeRepository : HomeRepository {
    val requestedSorts = mutableListOf<PostSort>()
    var updatedLike: Pair<String, Boolean>? = null
    var failLike = false

    override suspend fun getHome(sort: PostSort): PostContent {
        requestedSorts += sort
        return feedContent()
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int {
        updatedLike = photoId to isLiked
        if (failLike) error("like request failed")
        return if (isLiked) 25 else 24
    }
}

private class ControlledHomeRepository : HomeRepository {
    private val likeRequests = mutableListOf<CompletableDeferred<Int>>()

    override suspend fun getHome(sort: PostSort): PostContent = feedContent()

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int {
        val request = CompletableDeferred<Int>()
        likeRequests += request
        return request.await()
    }

    fun completeLike(
        requestIndex: Int,
        likeCount: Int,
    ) {
        likeRequests[requestIndex].complete(likeCount)
    }
}

private fun feedContent() = PostContent(
    dateLabel = "8월 3일 · 오늘의 주제",
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
