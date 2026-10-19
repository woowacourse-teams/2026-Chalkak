package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 가운데 표시선 아래로 눈금을 밀어 값을 고른다. 왼쪽으로 밀면 값이 커진다.
 *
 * 표시선을 지난 눈금은 잠깐 솟았다가 가라앉고, 손을 튕기듯 떼면 감속하며 더 굴러간다.
 * [automaticValue]가 있으면 그 자리에 점을 찍고, 기준값(자동 보정값, 없으면 0)에서는 조금 더 밀어야 벗어나도록 멈춘다.
 */
@Composable
fun AdjustmentRuler(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    automaticValue: Float? = null,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val unitWidthPx = with(density) { RulerUnitWidth.toPx() }
    val scale = remember(range, automaticValue) {
        RulerScale(
            range,
            anchor = automaticValue ?: 0f,
            detentHalfWidth = RulerDetentWidth.value / RulerUnitWidth.value,
        )
    }
    val currentScale by rememberUpdatedState(scale)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // 잔상과 햅틱은 손이나 관성으로 움직일 때만 낸다. 표시선을 지난 눈금과 지난 시각(나노초)을 기록한다.
    val passedTicks = remember { mutableStateMapOf<Int, Long>() }
    var frameNanos by remember { mutableLongStateOf(0L) }
    var inertia by remember { mutableStateOf<Job?>(null) }

    /** 위치에 맞는 값을 알린다. 범위 끝에 닿으면 false를 돌려준다. */
    fun emit(position: Float): Boolean {
        val ruler = currentScale
        val next = ruler.value(position)
        val previous = currentValue
        if (next != previous) {
            recordPassedTicks(passedTicks, ruler.range, previous, next, System.nanoTime())
            when {
                ruler.isAnchor(next) -> haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

                RulerScale.tickOf(next) != RulerScale.tickOf(previous) ->
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            currentOnValueChange(next)
        }
        return position in ruler.lowerPosition..ruler.upperPosition
    }

    fun stopInertia() {
        inertia?.cancel()
        inertia = null
    }

    fun decelerate(
        start: Float,
        initialVelocity: Float,
    ) {
        if (abs(initialVelocity) <= MIN_INERTIA_VELOCITY) return
        inertia = scope.launch {
            var position = start
            var velocity = initialVelocity
            var lastNanos = withFrameNanos { it }
            while (abs(velocity) > MIN_INERTIA_VELOCITY) {
                val now = withFrameNanos { it }
                val elapsedMillis = (now - lastNanos) / NANOS_PER_MILLI
                lastNanos = now
                position += velocity * elapsedMillis / MILLIS_PER_SECOND
                velocity *= DECELERATION_PER_MILLI.pow(elapsedMillis)
                if (!emit(position)) break
            }
        }
    }

    // 잔상이 남아 있는 동안만 매 프레임 다시 그리고, 다 가라앉으면 비운다.
    LaunchedEffect(passedTicks.isEmpty()) {
        while (passedTicks.isNotEmpty()) {
            frameNanos = withFrameNanos { it }
            passedTicks.entries.removeAll { frameNanos - it.value > LIFT_DURATION_NANOS }
        }
    }

    val tickColor = ChalkakTheme.colors.photoEditContent
    val markColor = ChalkakTheme.colors.photoEditContentMuted
    Canvas(
        modifier = modifier
            .height(RulerHeight)
            .semantics {
                contentDescription = label
                stateDescription = (value * PERCENT).roundToInt().toString()
                progressBarRangeInfo = ProgressBarRangeInfo(current = value, range = range)
                setProgress { target ->
                    currentOnValueChange(scale.snapped(target))
                    true
                }
            }.pointerInput(Unit) {
                // 굴러가는 중에 손을 대면 멈춘다.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    stopInertia()
                }
            }.pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                var startPosition = 0f
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        stopInertia()
                        velocityTracker.resetTracking()
                        startPosition = currentScale.position(currentValue)
                        dragged = 0f
                    },
                    onDragEnd = {
                        val velocity = -velocityTracker.calculateVelocity().x / unitWidthPx
                        decelerate(startPosition - dragged / unitWidthPx, velocity)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    dragged += dragAmount
                    emit(startPosition - dragged / unitWidthPx)
                }
            },
    ) {
        val centerX = size.width / 2f
        val tickSpacing = unitWidthPx / RulerScale.TICKS_PER_UNIT
        val tickWidth = RulerTickWidth.toPx()
        val tickHeight = RulerTickHeight.toPx()
        val liftedHeight = RulerLiftedTickHeight.toPx()
        val tickCount = ((range.endInclusive - range.start) * RulerScale.TICKS_PER_UNIT).roundToInt() + 1
        val startX = centerX - (value - range.start) * unitWidthPx

        for (index in 0 until tickCount) {
            val x = startX + index * tickSpacing
            if (x < 0f || x > size.width) continue
            val lift = liftAmount(passedTicks[index], frameNanos) *
                max(0f, 1f - abs(x - centerX) / (tickSpacing * LIFTED_TICK_SPAN))
            val height = tickHeight + (liftedHeight - tickHeight) * lift
            val baseAlpha = if (index % MAJOR_TICK_INTERVAL == 0) MAJOR_TICK_ALPHA else TICK_ALPHA
            drawRoundRect(
                color = tickColor.copy(alpha = baseAlpha + (1f - baseAlpha) * lift),
                topLeft = Offset(x - tickWidth / 2f, size.height - height),
                size = Size(tickWidth, height),
                cornerRadius = CornerRadius(tickWidth / 2f),
            )
        }

        if (automaticValue != null) {
            drawCircle(
                color = markColor,
                radius = RulerMarkSize.toPx() / 2f,
                center = Offset(
                    x = centerX + (automaticValue - value) * unitWidthPx,
                    y = size.height - RulerMarkCenterHeight.toPx(),
                ),
            )
        }

        val indicatorWidth = RulerIndicatorWidth.toPx()
        drawRoundRect(
            color = tickColor,
            topLeft = Offset(centerX - indicatorWidth / 2f, size.height - liftedHeight),
            size = Size(indicatorWidth, liftedHeight),
            cornerRadius = CornerRadius(indicatorWidth / 2f),
        )
    }
}

/** 손으로 움직이는 동안 표시선을 지난 눈금을 기록한다. */
private fun recordPassedTicks(
    passedTicks: MutableMap<Int, Long>,
    range: ClosedFloatingPointRange<Float>,
    oldValue: Float,
    newValue: Float,
    nowNanos: Long,
) {
    val oldPosition = (oldValue - range.start) * RulerScale.TICKS_PER_UNIT
    val newPosition = (newValue - range.start) * RulerScale.TICKS_PER_UNIT
    val first = ceil(minOf(oldPosition, newPosition)).toInt()
    val last = floor(maxOf(oldPosition, newPosition)).toInt()
    for (index in first..last) {
        passedTicks[index] = nowNanos
    }
}

/** 눈금이 표시선을 지난 직후 1이고 시간이 지나면 0으로 줄어든다. */
private fun liftAmount(
    passedAtNanos: Long?,
    nowNanos: Long,
): Float {
    if (passedAtNanos == null) return 0f
    return max(0f, 1f - (nowNanos - passedAtNanos).toFloat() / LIFT_DURATION_NANOS)
}

private const val PERCENT = 100f
private const val MAJOR_TICK_INTERVAL = 10
private const val TICK_ALPHA = 0.4f
private const val MAJOR_TICK_ALPHA = 0.75f

/** 잔상이 남는 눈금 수. */
private const val LIFTED_TICK_SPAN = 8f
private const val LIFT_DURATION_NANOS = 300_000_000L

/** 1ms마다 속도에 곱하는 비율. 손을 뗀 속도로 약 0.2초 동안 간 만큼 더 굴러간다. */
private const val DECELERATION_PER_MILLI = 0.995f

/** 값 기준 초당 속도. 이보다 느리면 관성을 멈춘다. */
private const val MIN_INERTIA_VELOCITY = 0.02f
private const val NANOS_PER_MILLI = 1_000_000f
private const val MILLIS_PER_SECOND = 1_000f

private val RulerHeight = 44.dp

/** 값 1.0이 차지하는 너비. 눈금 50칸 × 8dp. */
private val RulerUnitWidth = 400.dp

/** 기준값에서 멈추는 구간의 절반. 손가락으로 8dp를 더 밀어야 벗어난다. */
private val RulerDetentWidth = 8.dp
private val RulerTickWidth = 2.dp
private val RulerTickHeight = 20.dp

/** 표시선을 막 지난 눈금과 표시선의 높이. */
private val RulerLiftedTickHeight = 40.dp
private val RulerIndicatorWidth = 2.dp
private val RulerMarkSize = 6.dp
private val RulerMarkCenterHeight = 32.dp

@Preview(widthDp = 402)
@Composable
private fun AdjustmentRulerPreview() {
    ChalkakTheme {
        AdjustmentRuler(
            value = 0.24f,
            range = -1f..1f,
            label = "노출",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground)
                .padding(vertical = ChalkakTheme.spacing.lg),
            automaticValue = 0.1f,
        )
    }
}
