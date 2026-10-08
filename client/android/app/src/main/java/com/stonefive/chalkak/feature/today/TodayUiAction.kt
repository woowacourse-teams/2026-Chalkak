package com.stonefive.chalkak.feature.today

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem

sealed interface TodayUiAction {
    data object RetryClicked : TodayUiAction

    data object RefreshRequested : TodayUiAction

    data class EndThresholdChanged(val isReached: Boolean) : TodayUiAction

    data class LikeClicked(val photoId: String) : TodayUiAction

    data class BottomBarSelected(val item: ChalkakBottomBarItem) : TodayUiAction

    data object AddClicked : TodayUiAction
}
