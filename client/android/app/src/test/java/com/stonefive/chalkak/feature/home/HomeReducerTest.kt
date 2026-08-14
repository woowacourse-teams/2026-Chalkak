package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReducerTest {
    private val photo = Post(
        id = "photo-id",
        imageUrl = "https://example.com/photo.jpg",
        signatureUrl = null,
        contentDescription = "photo",
        title = "사진 제목",
        likeCount = 24,
    )

    @Test
    fun `좋아요 액션은 좋아요 상태와 개수를 함께 변경한다`() {
        val likedState = HomeUiState(photos = listOf(photo)).reduce(
            HomeUiAction.LikeClicked(photo.id),
        )

        assertTrue(photo.id in likedState.likedPhotoIds)
        assertEquals(
            25,
            likedState.photos
                .first()
                .likeCount,
        )

        val unlikedState = likedState.reduce(HomeUiAction.LikeClicked(photo.id))
        assertFalse(photo.id in unlikedState.likedPhotoIds)
        assertEquals(
            24,
            unlikedState.photos
                .first()
                .likeCount,
        )
    }

    @Test
    fun `좋아요 상태는 선택한 사진에만 적용한다`() {
        val secondPhoto = photo.copy(id = "second-photo", likeCount = 10)
        val state = HomeUiState(photos = listOf(photo, secondPhoto))
            .reduce(HomeUiAction.LikeClicked(secondPhoto.id))

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
    }

    @Test
    fun `정렬과 하단 탭 액션은 각 선택 상태를 변경한다`() {
        val sortedState = HomeUiState().reduce(
            HomeUiAction.SortSelected(PostSort.RANDOM),
        )
        val navigationState = sortedState.reduce(
            HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.DISPLAY),
        )

        assertEquals(PostSort.RANDOM, navigationState.selectedSort)
        assertEquals(ChalkakBottomBarItem.DISPLAY, navigationState.selectedBottomBarItem)
    }
}
