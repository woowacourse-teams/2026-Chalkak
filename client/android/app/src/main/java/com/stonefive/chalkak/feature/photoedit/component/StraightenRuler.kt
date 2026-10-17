package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PhotoEdit
import kotlin.math.abs
import kotlin.math.roundToInt

/** 수평 보정 눈금. 눈금을 좌우로 끌어 -45°~45°를 고르고, 0°를 지날 때 붙으며 진동한다. */
@Composable
fun StraightenRuler(
    degrees: Float,
    onDegreesChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentDegrees by rememberUpdatedState(degrees)
    val currentOnDegreesChange by rememberUpdatedState(onDegreesChange)
    val tickColor = ChalkakTheme.colors.photoEditContentMuted
    val indicatorColor = ChalkakTheme.colors.photoEditContent
    val roundedDegrees = degrees.roundToInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$roundedDegrees°",
            style = ChalkakTheme.typography.footnote,
            color = ChalkakTheme.colors.photoEditContent,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RulerHeight)
                .semantics {
                    contentDescription = "수평 보정"
                    stateDescription = "${roundedDegrees}도"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = degrees,
                        range = -MAX_DEGREES..MAX_DEGREES,
                    )
                    setProgress { target ->
                        currentOnDegreesChange(target.coerceIn(-MAX_DEGREES, MAX_DEGREES))
                        true
                    }
                }.pointerInput(Unit) {
                    val tickSpacing = RulerTickSpacing.toPx()
                    var dragDegrees = 0f
                    var emittedDegrees = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragDegrees = currentDegrees
                            emittedDegrees = currentDegrees
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragDegrees = (dragDegrees - dragAmount / tickSpacing).coerceIn(-MAX_DEGREES, MAX_DEGREES)
                        val snapped = if (abs(dragDegrees) < SNAP_DEGREES) 0f else dragDegrees
                        if (snapped == 0f && emittedDegrees != 0f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        emittedDegrees = snapped
                        currentOnDegreesChange(snapped)
                    }
                },
        ) {
            val tickSpacing = RulerTickSpacing.toPx()
            val centerX = size.width / 2f
            val minorHeight = RulerMinorTickHeight.toPx()
            val majorHeight = size.height
            val strokeWidth = RulerTickWidth.toPx()
            for (tick in -MAX_DEGREES.toInt()..MAX_DEGREES.toInt()) {
                val x = centerX + (tick - degrees) * tickSpacing
                if (x < 0f || x > size.width) continue
                val tickHeight = if (tick % MAJOR_TICK_INTERVAL == 0) majorHeight * MAJOR_TICK_RATIO else minorHeight
                val top = (size.height - tickHeight) / 2f
                drawLine(tickColor, Offset(x, top), Offset(x, top + tickHeight), strokeWidth)
            }
            drawLine(
                color = indicatorColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = RulerIndicatorWidth.toPx(),
            )
        }
    }
}

private const val MAX_DEGREES = PhotoEdit.MAX_STRAIGHTEN_DEGREES
private const val SNAP_DEGREES = 0.5f
private const val MAJOR_TICK_INTERVAL = 5
private const val MAJOR_TICK_RATIO = 0.8f
private val RulerHeight = 28.dp
private val RulerTickSpacing = 8.dp
private val RulerMinorTickHeight = 10.dp
private val RulerTickWidth = 1.dp
private val RulerIndicatorWidth = 2.dp

@Preview(widthDp = 360)
@Composable
private fun StraightenRulerPreview() {
    ChalkakTheme {
        StraightenRuler(
            degrees = 12f,
            onDegreesChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground)
                .padding(vertical = ChalkakTheme.spacing.sm),
        )
    }
}
