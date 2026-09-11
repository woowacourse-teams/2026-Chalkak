package com.stonefive.chalkak.feature.display

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.stonefive.chalkak.core.designsystem.scroll.BottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.rememberBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.settleCollapsingOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class DisplayScrollBehaviorState(
    private val gridState: LazyStaggeredGridState,
    private val settleScope: CoroutineScope,
    private val scrollToTopToggleThresholdPx: Float,
    private val hasFilter: Boolean,
    val bottomBarState: BottomBarScrollState,
) {
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

            if (available.y > 0f && hasFilter && filterHeight > 0 && filterOffset < 0f) {
                settleJob?.cancel()
                val previousOffset = filterOffset
                filterOffset = (filterOffset + available.y).coerceAtMost(0f)
                if (filterOffset != previousOffset) {
                    return Offset(0f, filterOffset - previousOffset)
                }
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

    fun updateScrollToTopVisibility() {
        if (!gridState.canScrollBackward && headerOffset == 0f && filterOffset == 0f) {
            bottomBarState.hideScrollToTopButton()
        }
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
    val bottomBarState = rememberBottomBarScrollState()
    return remember(
        gridState,
        settleScope,
        scrollToTopToggleThresholdPx,
        hasFilter,
        bottomBarState,
    ) {
        DisplayScrollBehaviorState(
            gridState = gridState,
            settleScope = settleScope,
            scrollToTopToggleThresholdPx = scrollToTopToggleThresholdPx,
            hasFilter = hasFilter,
            bottomBarState = bottomBarState,
        )
    }
}
