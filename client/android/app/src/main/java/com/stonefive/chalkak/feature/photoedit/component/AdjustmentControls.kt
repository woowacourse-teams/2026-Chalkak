package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PhotoAdjustment
import com.stonefive.chalkak.domain.model.PhotoAdjustments
import com.stonefive.chalkak.feature.photoedit.label
import kotlin.math.abs
import kotlin.math.roundToInt

/** 조정 탭 조작부. 위에는 고른 항목의 값과 슬라이더, 아래에는 항목 목록이 있다. */
@Composable
fun AdjustmentControls(
    adjustments: PhotoAdjustments,
    selectedAdjustment: PhotoAdjustment,
    onAdjustmentSelect: (PhotoAdjustment) -> Unit,
    onAdjustmentChange: (Float) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val value = adjustments[selectedAdjustment]
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            Text(
                text = value.toDisplayValue().toString(),
                modifier = Modifier.align(Alignment.Center),
                style = ChalkakTheme.typography.footnote,
                color = ChalkakTheme.colors.photoEditContent,
            )
            Text(
                text = "초기화",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        enabled = !adjustments.isIdentity,
                        role = Role.Button,
                        onClick = onResetClick,
                    ),
                style = ChalkakTheme.typography.footnote,
                color = if (adjustments.isIdentity) {
                    ChalkakTheme.colors.photoEditContentMuted
                } else {
                    ChalkakTheme.colors.photoEditContent
                },
            )
        }

        AdjustmentSlider(
            value = value,
            label = selectedAdjustment.label,
            onValueChange = onAdjustmentChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            PhotoAdjustment.entries.forEach { adjustment ->
                AdjustmentItem(
                    adjustment = adjustment,
                    isChanged = adjustments[adjustment] != 0f,
                    selected = adjustment == selectedAdjustment,
                    onClick = { onAdjustmentSelect(adjustment) },
                )
            }
        }
    }
}

@Composable
private fun AdjustmentItem(
    adjustment: PhotoAdjustment,
    isChanged: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ).padding(vertical = ChalkakTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
    ) {
        Text(
            text = adjustment.label,
            style = ChalkakTheme.typography.footnote,
            color = if (selected) ChalkakTheme.colors.photoEditContent else ChalkakTheme.colors.photoEditContentMuted,
        )
        // 값을 바꾼 항목은 점으로 표시한다.
        Box(
            modifier = Modifier
                .size(ChangedDotSize)
                .background(
                    color = if (isChanged) ChalkakTheme.colors.photoEditContent else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}

/** 가운데가 0인 -1..1 슬라이더. 0 근처에서는 0에 붙으며 진동한다. */
@Composable
private fun AdjustmentSlider(
    value: Float,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val trackColor = ChalkakTheme.colors.photoEditContentMuted
    val activeColor = ChalkakTheme.colors.photoEditContent

    fun emit(raw: Float) {
        val next = raw.coerceIn(-1f, 1f).let { if (abs(it) < SNAP_RANGE) 0f else it }
        if (next == 0f && currentValue != 0f) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        currentOnValueChange(next)
    }

    Canvas(
        modifier = modifier
            .height(SliderHeight)
            .semantics {
                contentDescription = label
                stateDescription = value.toDisplayValue().toString()
                progressBarRangeInfo = ProgressBarRangeInfo(current = value, range = -1f..1f)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(-1f, 1f))
                    true
                }
            }.pointerInput(Unit) {
                val trackStart = SliderThumbRadius.toPx()
                detectTapGestures { offset ->
                    emit((offset.x - trackStart) / (size.width - trackStart * 2f) * 2f - 1f)
                }
            }.pointerInput(Unit) {
                val trackWidth = size.width - SliderThumbRadius.toPx() * 2f
                var dragValue = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragValue = currentValue },
                ) { change, dragAmount ->
                    change.consume()
                    dragValue = (dragValue + dragAmount / trackWidth * 2f).coerceIn(-1f, 1f)
                    emit(dragValue)
                }
            },
    ) {
        val thumbRadius = SliderThumbRadius.toPx()
        val trackStart = thumbRadius
        val trackEnd = size.width - thumbRadius
        val centerY = size.height / 2f
        val centerX = (trackStart + trackEnd) / 2f
        val thumbX = centerX + value * (trackEnd - trackStart) / 2f
        val strokeWidth = SliderTrackWidth.toPx()
        drawLine(trackColor, Offset(trackStart, centerY), Offset(trackEnd, centerY), strokeWidth, StrokeCap.Round)
        drawLine(
            color = trackColor,
            start = Offset(centerX, centerY - SliderCenterTickHeight.toPx() / 2f),
            end = Offset(centerX, centerY + SliderCenterTickHeight.toPx() / 2f),
            strokeWidth = strokeWidth,
        )
        drawLine(activeColor, Offset(centerX, centerY), Offset(thumbX, centerY), strokeWidth, StrokeCap.Round)
        drawCircle(activeColor, thumbRadius, Offset(thumbX, centerY))
    }
}

private fun Float.toDisplayValue(): Int = (this * DISPLAY_SCALE).roundToInt()

private const val DISPLAY_SCALE = 100f
private const val SNAP_RANGE = 0.03f
private val SliderHeight = 32.dp
private val SliderThumbRadius = 9.dp
private val SliderTrackWidth = 2.dp
private val SliderCenterTickHeight = 12.dp
private val ChangedDotSize = 4.dp

@Preview(widthDp = 402)
@Composable
private fun AdjustmentControlsPreview() {
    ChalkakTheme {
        AdjustmentControls(
            adjustments = PhotoAdjustments(brightness = 0.24f, warmth = -0.4f),
            selectedAdjustment = PhotoAdjustment.Brightness,
            onAdjustmentSelect = {},
            onAdjustmentChange = {},
            onResetClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground),
            contentPadding = PaddingValues(ChalkakTheme.spacing.lg),
        )
    }
}
