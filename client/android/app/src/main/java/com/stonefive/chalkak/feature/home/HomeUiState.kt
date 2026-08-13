package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.HomePhoto
import com.stonefive.chalkak.domain.model.HomeSort

data class HomeUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val topic: String = "",
    val photos: List<HomePhoto> = emptyList(),
    val selectedSort: HomeSort = HomeSort.LATEST,
    val selectedBottomBarItem: ChalkakBottomBarItem = ChalkakBottomBarItem.TODAY,
    val likedPhotoIds: Set<String> = emptySet(),
    val expandedStoryPhotoIds: Set<String> = emptySet(),
)
