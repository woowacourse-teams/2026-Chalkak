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
import com.stonefive.chalkak.feature.photoedit.PhotoAdjustItem
import com.stonefive.chalkak.feature.photoedit.label
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 조정 탭 조작부. 위에는 고른 항목의 값과 슬라이더, 아래에는 맨 앞의 자동 보정과 항목 목록이 있다.
 * [appliedAutoAdjustments]가 있으면 항목 슬라이더에 자동 보정이 정한 값을 표시하고 그 값에 붙는다.
 */
@Composable
fun AdjustmentControls(
    adjustments: PhotoAdjustments,
    autoAdjustmentIntensity: Float,
    appliedAutoAdjustments: PhotoAdjustments?,
    selectedItem: PhotoAdjustItem,
    canReset: Boolean,
    onItemSelect: (PhotoAdjustItem) -> Unit,
    onValueChange: (Float) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    fun valueOf(item: PhotoAdjustItem): Float = when (item) {
        PhotoAdjustItem.Auto -> autoAdjustmentIntensity
        is PhotoAdjustItem.Manual -> adjustments[item.adjustment]
    }
    val value = valueOf(selectedItem)
    val automaticValue = (selectedItem as? PhotoAdjustItem.Manual)
        ?.let { appliedAutoAdjustments?.get(it.adjustment) }
        ?.takeIf { it != 0f }
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
                        enabled = canReset,
                        role = Role.Button,
                        onClick = onResetClick,
                    ),
                style = ChalkakTheme.typography.footnote,
                color = if (canReset) {
                    ChalkakTheme.colors.photoEditContent
                } else {
                    ChalkakTheme.colors.photoEditContentMuted
                },
            )
        }

        AdjustmentSlider(
            value = value,
            range = selectedItem.range,
            automaticValue = automaticValue,
            label = selectedItem.label,
            onValueChange = onValueChange,
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
            PhotoAdjustItem.entries.forEach { item ->
                AdjustmentItem(
                    item = item,
                    isChanged = valueOf(item) != 0f,
                    selected = item == selectedItem,
                    onClick = { onItemSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun AdjustmentItem(
    item: PhotoAdjustItem,
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
            text = item.label,
            style = ChalkakTheme.typography.footnote,
            color = if (selected) ChalkakTheme.colors.photoEditContent else ChalkakTheme.colors.photoEditContentMuted,
        )
        // 값을 바꾼 항목과 켜진 자동 보정은 점으로 표시한다.
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

/**
 * [range] 안의 값을 고르는 슬라이더. 0에 눈금을 두고, [automaticValue]가 있으면 그 자리에 점을 찍는다.
 * 기준값(자동 보정값, 없으면 0) 근처에서는 기준값에 붙으며 진동한다.
 */
@Composable
private fun AdjustmentSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    automaticValue: Float?,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(range)
    val anchor = (automaticValue ?: 0f).coerceIn(range)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val trackColor = ChalkakTheme.colors.photoEditContentMuted
    val activeColor = ChalkakTheme.colors.photoEditContent

    fun emit(raw: Float) {
        val clamped = raw.coerceIn(currentRange)
        val next = if (abs(clamped - currentAnchor) < SNAP_RANGE) currentAnchor else clamped
        if (next == currentAnchor && currentValue != currentAnchor) {
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
                progressBarRangeInfo = ProgressBarRangeInfo(current = value, range = range)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(range))
                    true
                }
            }.pointerInput(Unit) {
                val trackStart = SliderThumbRadius.toPx()
                detectTapGestures { offset ->
                    val fraction = (offset.x - trackStart) / (size.width - trackStart * 2f)
                    emit(currentRange.start + fraction * currentRange.span)
                }
            }.pointerInput(Unit) {
                val trackWidth = size.width - SliderThumbRadius.toPx() * 2f
                var dragValue = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragValue = currentValue },
                ) { change, dragAmount ->
                    change.consume()
                    dragValue = (dragValue + dragAmount / trackWidth * currentRange.span).coerceIn(currentRange)
                    emit(dragValue)
                }
            },
    ) {
        val thumbRadius = SliderThumbRadius.toPx()
        val trackStart = thumbRadius
        val trackEnd = size.width - thumbRadius
        val centerY = size.height / 2f
        fun xOf(position: Float) = trackStart + (position - range.start) / range.span * (trackEnd - trackStart)
        val strokeWidth = SliderTrackWidth.toPx()
        drawLine(trackColor, Offset(trackStart, centerY), Offset(trackEnd, centerY), strokeWidth, StrokeCap.Round)
        if (0f > range.start && 0f < range.endInclusive) {
            drawLine(
                color = trackColor,
                start = Offset(xOf(0f), centerY - SliderCenterTickHeight.toPx() / 2f),
                end = Offset(xOf(0f), centerY + SliderCenterTickHeight.toPx() / 2f),
                strokeWidth = strokeWidth,
            )
        }
        drawLine(activeColor, Offset(xOf(anchor), centerY), Offset(xOf(value), centerY), strokeWidth, StrokeCap.Round)
        if (automaticValue != null) {
            drawCircle(
                color = activeColor,
                radius = AutomaticMarkerRadius.toPx(),
                center = Offset(xOf(anchor), centerY - SliderCenterTickHeight.toPx()),
            )
        }
        drawCircle(activeColor, thumbRadius, Offset(xOf(value), centerY))
    }
}

private val ClosedFloatingPointRange<Float>.span: Float get() = endInclusive - start

private fun Float.toDisplayValue(): Int = (this * DISPLAY_SCALE).roundToInt()

private const val DISPLAY_SCALE = 100f
private const val SNAP_RANGE = 0.03f
private val SliderHeight = 32.dp
private val SliderThumbRadius = 9.dp
private val SliderTrackWidth = 2.dp
private val SliderCenterTickHeight = 12.dp
private val ChangedDotSize = 4.dp
private val AutomaticMarkerRadius = 2.dp

@Preview(widthDp = 402)
@Composable
private fun AdjustmentControlsPreview() {
    ChalkakTheme {
        AdjustmentControls(
            adjustments = PhotoAdjustments(exposure = 0.24f, shadows = 0.3f),
            autoAdjustmentIntensity = 1f,
            appliedAutoAdjustments = PhotoAdjustments(exposure = 0.14f, shadows = 0.3f),
            selectedItem = PhotoAdjustItem.Manual(PhotoAdjustment.Exposure),
            canReset = true,
            onItemSelect = {},
            onValueChange = {},
            onResetClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground),
            contentPadding = PaddingValues(ChalkakTheme.spacing.lg),
        )
    }
}
