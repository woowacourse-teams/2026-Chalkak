package com.stonefive.chalkak.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
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
import com.stonefive.chalkak.core.designsystem.scroll.COLLAPSING_SETTLE_DURATION_MILLIS
import com.stonefive.chalkak.core.designsystem.scroll.rememberBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.settleCollapsingOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val COLLAPSED_TOP_BAR_BACKGROUND_ALPHA = 0.86f
private const val TOP_BAR_FADE_START_PROGRESS = 0.8f

@Stable
internal class HomeScrollBehaviorState(
    private val photoListState: LazyListState,
    private val interactionScope: CoroutineScope,
    private val scrollToTopToggleThresholdPx: Float,
    val bottomBarState: BottomBarScrollState,
) {
    var topAreaOffset by mutableFloatStateOf(0f)
        private set
    var topAreaHeight by mutableIntStateOf(0)

    private var isTopAreaTargetHidden by mutableStateOf(false)
    private var settleJob: Job? = null

    val isTopAreaVisible: Boolean
        get() = topAreaHeight == 0 || topAreaOffset > -topAreaHeight.toFloat()

    val collapsedTopAreaProgress: Float
        get() = if (topAreaHeight == 0) {
            0f
        } else {
            (-topAreaOffset / topAreaHeight).coerceIn(0f, 1f)
        }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                bottomBarState.onScroll(
                    scrollDeltaY = available.y,
                    atTop = !photoListState.canScrollBackward && topAreaOffset == 0f,
                    toggleThresholdPx = scrollToTopToggleThresholdPx,
                )
            }

            if (available.y < 0f &&
                topAreaHeight > 0 &&
                topAreaOffset > -topAreaHeight.toFloat()
            ) {
                settleJob?.cancel()
                val previousOffset = topAreaOffset
                topAreaOffset = topAreaOffsetAfterScroll(
                    currentOffset = topAreaOffset,
                    scrollDelta = available.y,
                    areaHeight = topAreaHeight.toFloat(),
                )
                if (topAreaOffset != previousOffset) {
                    return Offset(0f, topAreaOffset - previousOffset)
                }
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y > 0f && topAreaOffset < 0f) {
                settleJob?.cancel()
                val previousOffset = topAreaOffset
                topAreaOffset = topAreaOffsetAfterScroll(
                    currentOffset = topAreaOffset,
                    scrollDelta = available.y,
                    areaHeight = topAreaHeight.toFloat(),
                )
                return Offset(0f, topAreaOffset - previousOffset)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            bottomBarState.settle(interactionScope)
            return Velocity.Zero
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity {
            settleTopArea()
            return Velocity.Zero
        }
    }

    fun visibleTopAreaHeight(fixedTopAreaHeightPx: Float): Float =
        (fixedTopAreaHeightPx + topAreaHeight + topAreaOffset).coerceAtLeast(0f)

    fun reset() {
        settleJob?.cancel()
        bottomBarState.reset()
        topAreaOffset = 0f
        isTopAreaTargetHidden = false
    }

    fun updateScrollToTopVisibility() {
        if (!photoListState.canScrollBackward && topAreaOffset == 0f) {
            bottomBarState.hideScrollToTopButton()
        }
    }

    private fun settleTopArea() {
        settleJob?.cancel()
        val initialOffset = topAreaOffset
        val targetOffset = settleCollapsingOffset(
            currentOffset = initialOffset,
            hiddenOffset = -topAreaHeight.toFloat(),
            restingOffset = if (isTopAreaTargetHidden) -topAreaHeight.toFloat() else 0f,
        )
        isTopAreaTargetHidden = targetOffset != 0f
        if (initialOffset == targetOffset) return

        settleJob = interactionScope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = COLLAPSING_SETTLE_DURATION_MILLIS),
            ) { value, _ -> topAreaOffset = value }
        }
    }
}

@Composable
internal fun rememberHomeScrollBehaviorState(
    photoListState: LazyListState,
    interactionScope: CoroutineScope,
    scrollToTopToggleThresholdPx: Float,
): HomeScrollBehaviorState {
    val bottomBarState = rememberBottomBarScrollState()
    return remember(photoListState, interactionScope, scrollToTopToggleThresholdPx, bottomBarState) {
        HomeScrollBehaviorState(
            photoListState = photoListState,
            interactionScope = interactionScope,
            scrollToTopToggleThresholdPx = scrollToTopToggleThresholdPx,
            bottomBarState = bottomBarState,
        )
    }
}

internal fun topBarBackgroundAlpha(collapsedProgress: Float): Float {
    val fadeProgress = (
        (collapsedProgress - TOP_BAR_FADE_START_PROGRESS) /
            (1f - TOP_BAR_FADE_START_PROGRESS)
        ).coerceIn(0f, 1f)
    return 1f - ((1f - COLLAPSED_TOP_BAR_BACKGROUND_ALPHA) * fadeProgress)
}

internal fun topAreaOffsetAfterScroll(
    currentOffset: Float,
    scrollDelta: Float,
    areaHeight: Float,
): Float = (currentOffset + scrollDelta).coerceIn(-areaHeight, 0f)
