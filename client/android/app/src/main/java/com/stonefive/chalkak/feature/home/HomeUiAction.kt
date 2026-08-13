package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.HomeSort

sealed interface HomeUiAction {
    data class SortSelected(val sort: HomeSort) : HomeUiAction

    data class LikeClicked(val photoId: String) : HomeUiAction

    data class StoryClicked(val photoId: String) : HomeUiAction

    data class BottomBarSelected(val item: ChalkakBottomBarItem) : HomeUiAction

    data object AddClicked : HomeUiAction
}
