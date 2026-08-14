package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort

data class HomeUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val topic: String = "",
    val photos: List<Post> = emptyList(),
    val selectedSort: PostSort = PostSort.LATEST,
    val selectedBottomBarItem: ChalkakBottomBarItem = ChalkakBottomBarItem.TODAY,
    val likedPhotoIds: Set<String> = emptySet(),
)
