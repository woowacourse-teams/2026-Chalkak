package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem

sealed interface HomeUiEvent {
    data object OpenPhotoUpload : HomeUiEvent

    data class NavigateToBottomBar(val item: ChalkakBottomBarItem) : HomeUiEvent

    data object LoadFailed : HomeUiEvent

    data object LikeUpdateFailed : HomeUiEvent
}
