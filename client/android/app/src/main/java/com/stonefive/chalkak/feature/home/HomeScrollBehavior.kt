package com.stonefive.chalkak.feature.home

private const val COLLAPSED_TOP_BAR_BACKGROUND_ALPHA = 0.86f
private const val TOP_BAR_FADE_START_PROGRESS = 0.8f

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
