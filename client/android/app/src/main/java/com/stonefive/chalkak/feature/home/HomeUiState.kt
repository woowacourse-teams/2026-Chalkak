package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.Photo
import com.stonefive.chalkak.domain.model.PhotoSort

data class HomeUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val topic: String = "",
    val photos: List<Photo> = emptyList(),
    val selectedSort: PhotoSort = PhotoSort.LATEST,
    val selectedBottomBarItem: ChalkakBottomBarItem = ChalkakBottomBarItem.TODAY,
    val likedPhotoIds: Set<String> = emptySet(),
    val expandedStoryPhotoIds: Set<String> = emptySet(),
)
