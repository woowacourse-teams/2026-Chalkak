package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem

sealed interface HomeUiEvent {
    data object ShowGuestLikeMessage : HomeUiEvent

    data class ShowRefreshFailure(val reason: HomeInitialError) : HomeUiEvent

    data object OpenPhotoUpload : HomeUiEvent

    data class NavigateToBottomBar(val item: ChalkakBottomBarItem) : HomeUiEvent
}
