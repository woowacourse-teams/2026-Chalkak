package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.PhotoSort

sealed interface HomeUiAction {
    data class SortSelected(val sort: PhotoSort) : HomeUiAction

    data class LikeClicked(val photoId: String) : HomeUiAction

    data class BottomBarSelected(val item: ChalkakBottomBarItem) : HomeUiAction

    data object AddClicked : HomeUiAction
}
