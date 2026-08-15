package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeHomeRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        repository = FakeHomeRepository()
        viewModel = HomeViewModel(repository)
    }

    @Test
    fun `화면 진입 시 홈 콘텐츠를 불러온다`() = runTest {
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("하늘하늘하늘", viewModel.uiState.value.topic)
        assertEquals(listOf(PostSort.LATEST), repository.requestedSorts)
    }

    @Test
    fun `정렬 액션은 선택 상태를 바꾸고 해당 정렬로 홈을 다시 불러온다`() = runTest {
        viewModel.onAction(HomeUiAction.SortSelected(PostSort.POPULAR))

        assertEquals(PostSort.POPULAR, viewModel.uiState.value.selectedSort)
        assertEquals(listOf(PostSort.LATEST, PostSort.POPULAR), repository.requestedSorts)
    }

    @Test
    fun `이전 정렬 요청이 나중에 완료되어도 최신 정렬 결과를 유지한다`() = runTest {
        val controlledRepository = ControlledHomeRepository()
        val controlledViewModel = HomeViewModel(controlledRepository)

        controlledViewModel.onAction(HomeUiAction.SortSelected(PostSort.POPULAR))
        controlledRepository.complete(
            sort = PostSort.POPULAR,
            content = homeContent(topic = "인기순 결과"),
        )

        assertEquals(PostSort.POPULAR, controlledViewModel.uiState.value.selectedSort)
        assertEquals("인기순 결과", controlledViewModel.uiState.value.topic)
        assertFalse(controlledViewModel.uiState.value.isLoading)

        controlledRepository.complete(
            sort = PostSort.LATEST,
            content = homeContent(topic = "최신순 결과"),
        )

        assertEquals(PostSort.POPULAR, controlledViewModel.uiState.value.selectedSort)
        assertEquals("인기순 결과", controlledViewModel.uiState.value.topic)
        assertFalse(controlledViewModel.uiState.value.isLoading)
    }

    @Test
    fun `좋아요 액션은 선택한 사진 상태와 저장소를 함께 갱신한다`() = runTest {
        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))

        assertEquals(setOf(PHOTO_ID), viewModel.uiState.value.likedPhotoIds)
        assertEquals(
            25,
            viewModel.uiState.value.photos
                .first()
                .likeCount,
        )
        assertEquals(PHOTO_ID, repository.updatedPhotoId)
        assertEquals(true, repository.updatedIsLiked)
    }

    @Test
    fun `좋아요 실패는 해당 사진만 복원하고 새 정렬 상태를 유지한다`() = runTest {
        val controlledRepository = ControlledLikeRepository()
        val controlledViewModel = HomeViewModel(controlledRepository)

        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledViewModel.onAction(HomeUiAction.SortSelected(PostSort.POPULAR))
        controlledRepository.failLike(requestIndex = 0)

        assertEquals(PostSort.POPULAR, controlledViewModel.uiState.value.selectedSort)
        assertEquals(emptySet<String>(), controlledViewModel.uiState.value.likedPhotoIds)
        assertEquals(
            24,
            controlledViewModel.uiState.value.photos
                .first()
                .likeCount,
        )
    }

    @Test
    fun `이전 좋아요 성공 응답이 나중에 완료되어도 최신 요청 결과를 유지한다`() = runTest {
        val controlledRepository = ControlledLikeRepository()
        val controlledViewModel = HomeViewModel(controlledRepository)

        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledRepository.completeLike(requestIndex = 1, likeCount = 24)
        controlledRepository.completeLike(requestIndex = 0, likeCount = 25)

        assertEquals(emptySet<String>(), controlledViewModel.uiState.value.likedPhotoIds)
        assertEquals(
            24,
            controlledViewModel.uiState.value.photos
                .first()
                .likeCount,
        )
    }

    @Test
    fun `이전 좋아요 실패 응답은 최신 낙관적 상태를 복원하지 않는다`() = runTest {
        val controlledRepository = ControlledLikeRepository()
        val controlledViewModel = HomeViewModel(controlledRepository)

        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        controlledRepository.failLike(requestIndex = 0)

        assertEquals(setOf(PHOTO_ID), controlledViewModel.uiState.value.likedPhotoIds)
        assertEquals(
            25,
            controlledViewModel.uiState.value.photos
                .first()
                .likeCount,
        )
    }

    @Test
    fun `홈 재조회 후 이전 좋아요 요청이 실패해도 최신 홈 콘텐츠를 유지한다`() = runTest {
        val reloadRepository = ReloadDuringLikeRepository()
        val reloadViewModel = HomeViewModel(reloadRepository)

        reloadViewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        reloadViewModel.onAction(HomeUiAction.SortSelected(PostSort.POPULAR))

        assertEquals(
            30,
            reloadViewModel.uiState.value.photos
                .first()
                .likeCount,
        )
        assertEquals(setOf(PHOTO_ID), reloadViewModel.uiState.value.likedPhotoIds)

        reloadRepository.failLike()

        assertEquals(PostSort.POPULAR, reloadViewModel.uiState.value.selectedSort)
        assertEquals(
            30,
            reloadViewModel.uiState.value.photos
                .first()
                .likeCount,
        )
        assertEquals(setOf(PHOTO_ID), reloadViewModel.uiState.value.likedPhotoIds)
    }

    @Test
    fun `추가 액션은 업로드 열기 이벤트를 전달한다`() = runTest {
        viewModel.onAction(HomeUiAction.AddClicked)

        assertEquals(HomeUiEvent.OpenPhotoUpload, viewModel.uiEvent.first())
    }
}

private const val PHOTO_ID = "photo-1"

private fun homeContent(
    topic: String = "하늘하늘하늘",
    likeCount: Int = 24,
    likedPhotoIds: Set<String> = emptySet(),
) = PostContent(
    dateLabel = "8월 3일 · 오늘의 주제",
    topic = topic,
    photos = listOf(
        Post(
            id = PHOTO_ID,
            imageUrl = "https://example.com/photo.jpg",
            signatureUrl = "https://example.com/signature.png",
            contentDescription = "하늘",
            title = "사진 제목",
            likeCount = likeCount,
        ),
    ),
    likedPhotoIds = likedPhotoIds,
)

private class FakeHomeRepository : HomeRepository {
    val requestedSorts = mutableListOf<PostSort>()
    var updatedPhotoId: String? = null
    var updatedIsLiked: Boolean? = null

    override suspend fun getHome(sort: PostSort): PostContent {
        requestedSorts += sort
        return homeContent()
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int {
        updatedPhotoId = photoId
        updatedIsLiked = isLiked
        return if (isLiked) 25 else 24
    }
}

private class ControlledHomeRepository : HomeRepository {
    private val responses = mutableMapOf<PostSort, CompletableDeferred<PostContent>>()

    override suspend fun getHome(sort: PostSort): PostContent {
        val response = CompletableDeferred<PostContent>()
        responses[sort] = response
        return response.await()
    }

    fun complete(
        sort: PostSort,
        content: PostContent,
    ) {
        checkNotNull(responses[sort]).complete(content)
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int = error("Not used")
}

private class ControlledLikeRepository : HomeRepository {
    private val likeResponses = mutableListOf<CompletableDeferred<Int>>()

    override suspend fun getHome(sort: PostSort): PostContent = homeContent()

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int {
        val response = CompletableDeferred<Int>()
        likeResponses += response
        return response.await()
    }

    fun completeLike(
        requestIndex: Int,
        likeCount: Int,
    ) {
        likeResponses[requestIndex].complete(likeCount)
    }

    fun failLike(requestIndex: Int) {
        likeResponses[requestIndex].completeExceptionally(IllegalStateException("Like update failed"))
    }
}

private class ReloadDuringLikeRepository : HomeRepository {
    private val likeResponse = CompletableDeferred<Int>()
    private var homeRequestCount = 0

    override suspend fun getHome(sort: PostSort): PostContent {
        homeRequestCount++
        return if (homeRequestCount == 1) {
            homeContent()
        } else {
            homeContent(
                likeCount = 30,
                likedPhotoIds = setOf(PHOTO_ID),
            )
        }
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int = likeResponse.await()

    fun failLike() {
        likeResponse.completeExceptionally(IllegalStateException("Like update failed"))
    }
}
