package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.PostSort

sealed interface HomeUiAction {
    data object RetryClicked : HomeUiAction

    data object RefreshRequested : HomeUiAction

    data class SortSelected(val sort: PostSort) : HomeUiAction

    data class EndThresholdChanged(val isReached: Boolean) : HomeUiAction

    data class LikeClicked(val photoId: String) : HomeUiAction

    data class BottomBarSelected(val item: ChalkakBottomBarItem) : HomeUiAction

    data object AddClicked : HomeUiAction
}
