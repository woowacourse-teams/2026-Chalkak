package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem

sealed interface HomeUiAction {
    data class LikeClicked(val photoId: String) : HomeUiAction

    data class BottomBarSelected(val item: ChalkakBottomBarItem) : HomeUiAction

    data object AddClicked : HomeUiAction
}
