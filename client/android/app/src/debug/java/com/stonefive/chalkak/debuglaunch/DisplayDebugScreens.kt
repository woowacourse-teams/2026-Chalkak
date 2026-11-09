package com.stonefive.chalkak.debuglaunch

import androidx.compose.runtime.Composable
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.display.DisplayContentState
import com.stonefive.chalkak.feature.display.DisplayScreen
import com.stonefive.chalkak.feature.display.DisplayUiState
import com.stonefive.chalkak.feature.display.component.previewDisplayPhotos
import java.time.LocalDate

/**
 * 전시 화면(#989)
 * - display-refreshing: 최신 전시(정렬 탭 있음)를 당겨서 새로고침하는 중. 새로고침 인디케이터가 멈춰 보이는 상태.
 *   새로고침 중 상태는 DisplayViewModel.refresh()와 같이 보던 상태에 isRefreshing만 켠다(서버 요청은 보내지 않는다).
 */
internal val displayDebugScreens: Map<String, @Composable () -> Unit> = mapOf(
    "display-refreshing" to { DebugDisplay(latestDisplayState().copy(isRefreshing = true)) },
)

private fun latestDisplayState(): DisplayUiState {
    val today = LocalDate.of(2026, 8, 5)
    return DisplayUiState(
        selectedDate = today,
        latestDate = today,
        earliestDate = today.minusDays(4),
        topic = "바다",
        content = DisplayContentState.Latest(
            photos = previewDisplayPhotos,
            selectedSort = PostSort.RANDOM,
        ),
    )
}

@Composable
private fun DebugDisplay(state: DisplayUiState) {
    DisplayScreen(
        uiState = state,
        onPreviousDateClick = {},
        onNextDateClick = {},
        onSortSelected = {},
        onFeaturedPageChanged = {},
        onEndThresholdChanged = {},
        onOpenPhotoUpload = {},
        onNavigateToBottomBar = {},
    )
}
