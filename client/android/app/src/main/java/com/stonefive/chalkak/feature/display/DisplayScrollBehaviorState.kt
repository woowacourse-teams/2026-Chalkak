package com.stonefive.chalkak.feature.display

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.rememberScreenBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.BottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.settleCollapsingOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SCROLL_TO_TOP_ANIMATED_ITEM_COUNT = 12

@Stable
class DisplayScrollBehaviorState(
    gridState: LazyStaggeredGridState,
    private val settleScope: CoroutineScope,
    private val scrollToTopToggleThresholdPx: Float,
    hasFilter: Boolean,
    val bottomBarState: BottomBarScrollState,
) {
    companion object {
        fun saver(
            gridState: LazyStaggeredGridState,
            settleScope: CoroutineScope,
            scrollToTopToggleThresholdPx: Float,
            hasFilter: Boolean,
            bottomBarState: BottomBarScrollState,
        ) = listSaver<DisplayScrollBehaviorState, Any>(
            save = {
                listOf(
                    it.headerOffset,
                    it.headerHeight,
                    it.filterOffset,
                    it.filterHeight,
                    it.isHeaderTargetHidden,
                    it.isFilterTargetHidden,
                    it.bottomBarState.offset,
                    it.bottomBarState.height,
                    it.bottomBarState.isTargetHidden,
                )
            },
            restore = { saved ->
                DisplayScrollBehaviorState(
                    gridState,
                    settleScope,
                    scrollToTopToggleThresholdPx,
                    hasFilter,
                    bottomBarState,
                ).apply {
                    headerOffset = saved[0] as Float
                    headerHeight = saved[1] as Int
                    filterOffset = saved[2] as Float
                    filterHeight = saved[3] as Int
                    isHeaderTargetHidden = saved[4] as Boolean
                    isFilterTargetHidden = saved[5] as Boolean
                    bottomBarState.restoreState(
                        offset = saved[6] as Float,
                        height = saved[7] as Int,
                        isTargetHidden = saved[8] as Boolean,
                    )
                }
            },
        )
    }

    private var gridState = gridState
    private var hasFilter = hasFilter
    var headerOffset by mutableFloatStateOf(0f)
        private set
    var headerHeight by mutableIntStateOf(0)
    var filterOffset by mutableFloatStateOf(0f)
        private set
    var filterHeight by mutableIntStateOf(0)

    private var isHeaderTargetHidden by mutableStateOf(false)
    private var isFilterTargetHidden by mutableStateOf(false)
    private var settleJob: Job? = null

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                bottomBarState.onScroll(
                    scrollDeltaY = available.y,
                    atTop = !gridState.canScrollBackward &&
                        headerOffset == 0f &&
                        filterOffset == 0f,
                    toggleThresholdPx = scrollToTopToggleThresholdPx,
                )
            }

            if (available.y < 0f) {
                settleJob?.cancel()
                var remainingScroll = available.y
                var consumedScroll = 0f

                if (headerHeight > 0) {
                    val previousOffset = headerOffset
                    headerOffset = (headerOffset + remainingScroll)
                        .coerceAtLeast(-headerHeight.toFloat())
                    val consumedByHeader = headerOffset - previousOffset
                    consumedScroll += consumedByHeader
                    remainingScroll -= consumedByHeader
                }

                if (remainingScroll < 0f && hasFilter && filterHeight > 0) {
                    val previousOffset = filterOffset
                    filterOffset = (filterOffset + remainingScroll)
                        .coerceAtLeast(-filterHeight.toFloat())
                    consumedScroll += filterOffset - previousOffset
                }

                if (consumedScroll != 0f) return Offset(0f, consumedScroll)
            }

            if (available.y > 0f) {
                val consumedScroll = expandTopAreas(available.y)
                if (consumedScroll != 0f) return Offset(0f, consumedScroll)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y > 0f && headerHeight > 0 && headerOffset < 0f) {
                settleJob?.cancel()
                val previousOffset = headerOffset
                headerOffset = (headerOffset + available.y).coerceAtMost(0f)
                return Offset(0f, headerOffset - previousOffset)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            bottomBarState.settle(settleScope)
            return Velocity.Zero
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity {
            settleTopAreas()
            return Velocity.Zero
        }
    }

    fun visibleTopAreaHeight(statusBarHeightPx: Int): Float {
        val filterContribution = if (hasFilter) filterHeight + filterOffset else 0f
        return (statusBarHeightPx + headerHeight + headerOffset + filterContribution)
            .coerceAtLeast(0f)
    }

    fun reset() {
        settleJob?.cancel()
        bottomBarState.reset()
        headerOffset = 0f
        isHeaderTargetHidden = false
        filterOffset = 0f
        isFilterTargetHidden = false
    }

    suspend fun scrollToTop() {
        reset()
        if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_ANIMATED_ITEM_COUNT) {
            gridState.scrollToItem(SCROLL_TO_TOP_ANIMATED_ITEM_COUNT)
        }
        gridState.animateScrollToItem(0)
    }

    fun updateLayout(
        gridState: LazyStaggeredGridState,
        hasFilter: Boolean,
    ) {
        this.gridState = gridState
        this.hasFilter = hasFilter
    }

    fun updateScrollToTopVisibility() {
        if (!gridState.canScrollBackward && headerOffset == 0f && filterOffset == 0f) {
            bottomBarState.hideScrollToTopButton()
        }
    }

    /**
     * 아래로 끄는 양 [downwardScroll](양수)을 스크롤 전 단계에서 필터 → 헤더 순으로 펼치는 데 쓰고, 쓴 양을 돌려준다.
     * 필터는 목록 위치와 상관없이 펼친다. 헤더는 목록이 맨 위일 때만 여기서 펼친다(#988).
     * 맨 위에서 남은 끌기는 안쪽의 당겨서 새로고침이 받으므로, 헤더를 다 펼친 뒤에만 새로고침이 당겨진다.
     * 예: 헤더 200·필터 100이 모두 접힌 맨 위에서 120을 끌면 필터 100, 헤더 20을 쓰고 120을 돌려준다.
     */
    private fun expandTopAreas(downwardScroll: Float): Float {
        var remainingScroll = downwardScroll
        if (hasFilter && filterHeight > 0 && filterOffset < 0f) {
            settleJob?.cancel()
            val previousOffset = filterOffset
            filterOffset = (filterOffset + remainingScroll).coerceAtMost(0f)
            remainingScroll -= filterOffset - previousOffset
        }
        if (remainingScroll > 0f && !gridState.canScrollBackward && headerHeight > 0 && headerOffset < 0f) {
            settleJob?.cancel()
            val previousOffset = headerOffset
            headerOffset = (headerOffset + remainingScroll).coerceAtMost(0f)
            remainingScroll -= headerOffset - previousOffset
        }
        return downwardScroll - remainingScroll
    }

    private fun settleTopAreas() {
        settleJob?.cancel()
        val initialHeaderOffset = headerOffset
        val initialFilterOffset = filterOffset
        val targetHeaderOffset = settleCollapsingOffset(
            currentOffset = initialHeaderOffset,
            hiddenOffset = -headerHeight.toFloat(),
            restingOffset = if (isHeaderTargetHidden) -headerHeight.toFloat() else 0f,
        )
        val targetFilterOffset = settleCollapsingOffset(
            currentOffset = initialFilterOffset,
            hiddenOffset = -filterHeight.toFloat(),
            restingOffset = if (isFilterTargetHidden) -filterHeight.toFloat() else 0f,
        )
        isHeaderTargetHidden = targetHeaderOffset != 0f
        isFilterTargetHidden = targetFilterOffset != 0f

        if (initialHeaderOffset == targetHeaderOffset &&
            initialFilterOffset == targetFilterOffset
        ) {
            return
        }

        settleJob = settleScope.launch {
            animate(initialValue = 0f, targetValue = 1f) { progress, _ ->
                headerOffset = initialHeaderOffset +
                    ((targetHeaderOffset - initialHeaderOffset) * progress)
                filterOffset = initialFilterOffset +
                    ((targetFilterOffset - initialFilterOffset) * progress)
            }
        }
    }
}

@Composable
fun rememberDisplayScrollBehaviorState(
    gridState: LazyStaggeredGridState,
    settleScope: CoroutineScope,
    scrollToTopToggleThresholdPx: Float,
    hasFilter: Boolean,
): DisplayScrollBehaviorState {
    val bottomBarState = rememberScreenBottomBarScrollState(ChalkakBottomBarItem.DISPLAY)
    val state = rememberSaveable(
        scrollToTopToggleThresholdPx,
        saver = DisplayScrollBehaviorState.saver(
            gridState = gridState,
            settleScope = settleScope,
            scrollToTopToggleThresholdPx = scrollToTopToggleThresholdPx,
            hasFilter = hasFilter,
            bottomBarState = bottomBarState,
        ),
    ) {
        DisplayScrollBehaviorState(
            gridState = gridState,
            settleScope = settleScope,
            scrollToTopToggleThresholdPx = scrollToTopToggleThresholdPx,
            hasFilter = hasFilter,
            bottomBarState = bottomBarState,
        )
    }
    state.updateLayout(
        gridState = gridState,
        hasFilter = hasFilter,
    )
    return state
}
