package com.stonefive.chalkak.core.designsystem.scroll

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal const val COLLAPSING_SETTLE_DURATION_MILLIS = 220
internal val CollapsingScrollToTopThreshold = 12.dp
internal const val COLLAPSING_FLOATING_BACKGROUND_ALPHA = 0.86f

private const val SETTLE_BREAK_THRESHOLD = 0.05f

internal fun Modifier.collapsingArea(offset: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val offsetPx = offset.toInt().coerceIn(-placeable.height, 0)
    val visibleHeight = (placeable.height + offsetPx).coerceAtLeast(0)

    layout(placeable.width, visibleHeight) {
        placeable.placeRelative(0, offsetPx)
    }
}

internal fun settleCollapsingOffset(
    currentOffset: Float,
    hiddenOffset: Float,
    restingOffset: Float,
): Float {
    if (hiddenOffset == 0f) return 0f

    val restingProgress = restingOffset / hiddenOffset
    val currentProgress = (currentOffset / hiddenOffset).coerceIn(0f, 1f)
    val movedFromResting = abs(currentProgress - restingProgress)
    if (movedFromResting <= SETTLE_BREAK_THRESHOLD) return restingOffset
    return if (restingProgress == 0f) hiddenOffset else 0f
}

internal fun bottomBarOffsetAfterScroll(
    currentOffset: Float,
    scrollDelta: Float,
    barHeight: Float,
): Float = (currentOffset - scrollDelta).coerceIn(0f, barHeight)

internal data class ScrollToTopButtonState(
    val accumulated: Float,
    val visible: Boolean,
)

internal fun scrollToTopButtonStateAfterScroll(
    state: ScrollToTopButtonState,
    scrollDelta: Float,
    threshold: Float,
): ScrollToTopButtonState {
    val base = when {
        scrollDelta > 0f && state.accumulated < 0f -> 0f
        scrollDelta < 0f && state.accumulated > 0f -> 0f
        else -> state.accumulated
    }
    val accumulated = base + scrollDelta
    val visible = when {
        accumulated >= threshold -> true
        accumulated <= -threshold -> false
        else -> state.visible
    }
    return ScrollToTopButtonState(accumulated = accumulated, visible = visible)
}
