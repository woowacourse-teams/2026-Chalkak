package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.Photo
import com.stonefive.chalkak.domain.model.PhotoSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReducerTest {
    private val photoWithStory = Photo(
        id = "photo-id",
        imageUrl = "https://example.com/photo.jpg",
        signatureUrl = null,
        contentDescription = "photo",
        story = "안녕하세요 감사합니다.",
        likeCount = 24,
    )

    @Test
    fun `좋아요 액션은 좋아요 상태와 개수를 함께 변경한다`() {
        val likedState = HomeUiState(photos = listOf(photoWithStory)).reduce(
            HomeUiAction.LikeClicked(photoWithStory.id),
        )

        assertTrue(photoWithStory.id in likedState.likedPhotoIds)
        assertEquals(
            25,
            likedState.photos
                .first()
                .likeCount,
        )

        val unlikedState = likedState.reduce(HomeUiAction.LikeClicked(photoWithStory.id))
        assertFalse(photoWithStory.id in unlikedState.likedPhotoIds)
        assertEquals(
            24,
            unlikedState.photos
                .first()
                .likeCount,
        )
    }

    @Test
    fun `이야기가 있는 사진은 이야기 영역을 열고 닫는다`() {
        val expandedState = HomeUiState(photos = listOf(photoWithStory)).reduce(
            HomeUiAction.StoryClicked(photoWithStory.id),
        )

        assertTrue(photoWithStory.id in expandedState.expandedStoryPhotoIds)
        assertFalse(
            photoWithStory.id in expandedState
                .reduce(HomeUiAction.StoryClicked(photoWithStory.id))
                .expandedStoryPhotoIds,
        )
    }

    @Test
    fun `이야기가 없는 사진은 이야기 영역을 열지 않는다`() {
        val state = HomeUiState(
            photos = listOf(photoWithStory.copy(story = null)),
            expandedStoryPhotoIds = setOf(photoWithStory.id),
        )

        assertFalse(
            photoWithStory.id in state
                .reduce(HomeUiAction.StoryClicked(photoWithStory.id))
                .expandedStoryPhotoIds,
        )
    }

    @Test
    fun `좋아요와 이야기 상태는 선택한 사진에만 적용한다`() {
        val secondPhoto = photoWithStory.copy(id = "second-photo", likeCount = 10)
        val state = HomeUiState(photos = listOf(photoWithStory, secondPhoto))
            .reduce(HomeUiAction.LikeClicked(secondPhoto.id))
            .reduce(HomeUiAction.StoryClicked(secondPhoto.id))

        assertEquals(
            24,
            state.photos
                .first()
                .likeCount,
        )
        assertEquals(
            11,
            state.photos
                .last()
                .likeCount,
        )
        assertEquals(setOf(secondPhoto.id), state.likedPhotoIds)
        assertEquals(setOf(secondPhoto.id), state.expandedStoryPhotoIds)
    }

    @Test
    fun `정렬과 하단 탭 액션은 각 선택 상태를 변경한다`() {
        val sortedState = HomeUiState(
            expandedStoryPhotoIds = setOf(photoWithStory.id),
        ).reduce(
            HomeUiAction.SortSelected(PhotoSort.RANDOM),
        )
        val navigationState = sortedState.reduce(
            HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.DISPLAY),
        )

        assertEquals(PhotoSort.RANDOM, navigationState.selectedSort)
        assertEquals(ChalkakBottomBarItem.DISPLAY, navigationState.selectedBottomBarItem)
        assertTrue(navigationState.expandedStoryPhotoIds.isEmpty())
    }
}
