package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.HomeContent
import com.stonefive.chalkak.domain.model.HomeImage
import com.stonefive.chalkak.domain.model.HomePhoto
import com.stonefive.chalkak.domain.model.HomeSort
import com.stonefive.chalkak.domain.repository.HomeRepository
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
        assertEquals(listOf(HomeSort.LATEST), repository.requestedSorts)
    }

    @Test
    fun `정렬 액션은 선택 상태를 바꾸고 해당 정렬로 홈을 다시 불러온다`() = runTest {
        viewModel.onAction(HomeUiAction.SortSelected(HomeSort.POPULAR))

        assertEquals(HomeSort.POPULAR, viewModel.uiState.value.selectedSort)
        assertEquals(listOf(HomeSort.LATEST, HomeSort.POPULAR), repository.requestedSorts)
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
    fun `추가 액션은 업로드 열기 이벤트를 전달한다`() = runTest {
        viewModel.onAction(HomeUiAction.AddClicked)

        assertEquals(HomeUiEvent.OpenPhotoUpload, viewModel.uiEvent.first())
    }
}

private const val PHOTO_ID = "photo-1"

private class FakeHomeRepository : HomeRepository {
    val requestedSorts = mutableListOf<HomeSort>()
    var updatedPhotoId: String? = null
    var updatedIsLiked: Boolean? = null

    override suspend fun getHome(sort: HomeSort): HomeContent {
        requestedSorts += sort
        return HomeContent(
            dateLabel = "8월 3일 · 오늘의 주제",
            topic = "하늘하늘하늘",
            photos = listOf(
                HomePhoto(
                    id = PHOTO_ID,
                    image = HomeImage.Local(0),
                    signatureImage = null,
                    contentDescription = "하늘",
                    story = "이야기",
                    likeCount = 24,
                ),
            ),
            likedPhotoIds = emptySet(),
        )
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
