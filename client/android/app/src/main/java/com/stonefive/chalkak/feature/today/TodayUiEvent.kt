package com.stonefive.chalkak.feature.today

import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem

sealed interface TodayUiEvent {
    data object OpenPhotoUpload : TodayUiEvent

    data class NavigateToBottomBar(val item: ChalkakBottomBarItem) : TodayUiEvent
}
