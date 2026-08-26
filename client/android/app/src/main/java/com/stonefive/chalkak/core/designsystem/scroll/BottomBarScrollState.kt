package com.stonefive.chalkak.core.designsystem.scroll

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
internal class BottomBarScrollState {
    var offset by mutableFloatStateOf(0f)
    var height by mutableIntStateOf(0)
    var isScrollToTopButtonVisible by mutableStateOf(false)
        private set

    private var isTargetHidden by mutableStateOf(false)
    private var accumulated by mutableFloatStateOf(0f)
    private var restoreJob: Job? = null

    fun onScroll(
        scrollDeltaY: Float,
        atTop: Boolean,
        toggleThresholdPx: Float,
    ) {
        restoreJob?.cancel()
        if (atTop) {
            accumulated = 0f
            isScrollToTopButtonVisible = false
        } else {
            val next = scrollToTopButtonStateAfterScroll(
                state = ScrollToTopButtonState(
                    accumulated = accumulated,
                    visible = isScrollToTopButtonVisible,
                ),
                scrollDelta = scrollDeltaY,
                threshold = toggleThresholdPx,
            )
            accumulated = next.accumulated
            isScrollToTopButtonVisible = next.visible
        }
        offset = bottomBarOffsetAfterScroll(
            currentOffset = offset,
            scrollDelta = scrollDeltaY,
            barHeight = height.toFloat(),
        )
    }

    fun settle(scope: CoroutineScope) {
        restoreJob?.cancel()
        val initialOffset = offset
        val hiddenOffset = height.toFloat()

        val targetOffset = settleCollapsingOffset(
            currentOffset = initialOffset,
            hiddenOffset = hiddenOffset,
            restingOffset = if (isTargetHidden) hiddenOffset else 0f,
        )
        isTargetHidden = targetOffset != 0f

        if (initialOffset == targetOffset) return

        restoreJob = scope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = COLLAPSING_SETTLE_DURATION_MILLIS),
            ) { value, _ -> offset = value }
        }
    }

    fun hideScrollToTopButton() {
        isScrollToTopButtonVisible = false
    }

    fun reset() {
        restoreJob?.cancel()
        offset = 0f
        isTargetHidden = false
        isScrollToTopButtonVisible = false
        accumulated = 0f
    }
}

@Composable
internal fun rememberBottomBarScrollState(): BottomBarScrollState = remember { BottomBarScrollState() }
