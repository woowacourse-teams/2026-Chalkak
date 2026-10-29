package com.stonefive.chalkak.feature.record.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.aspectRatioForSize
import com.stonefive.chalkak.core.designsystem.component.image.rememberSharedPhotoSource
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 월간 상단의 사진 묶음(기둥과 눈금자).
 * - 1일이 맨 왼쪽, 가장 최근 날이 맨 오른쪽에 가는 기둥으로 서 있고, 고른 날만 넓게 펼쳐진다.
 * - 기둥 아래에 날마다 눈금, 일요일마다 긴 눈금, 5일마다 숫자를 둔다. 위의 삼각 표시가 고른 날을 가리킨다.
 * - 펼친 기둥은 사진 원본 비율대로 폭을 잡는다. 높이는 늘 같고, 폭 상한을 넘는 넓은 사진만 양옆을 잘라 보인다.
 * - 좌우로 끌면 스크롤처럼 넘어간다(오른쪽으로 끌면 지난 날, 왼쪽으로 끌면 최근 날).
 *   하루마다 잠깐 걸렸다가 넘어가고, 하루를 넘을 때마다 햅틱을 준다.
 *   튕기듯 떼면 감속하며 하루씩 더 넘어간 뒤 가까운 날에 붙는다.
 * - 펼친 사진을 누르면 그 사진의 피드로 간다. 다른 기둥을 누르면 그날을 펼친다.
 *
 * @param position 고른 날의 순서(가장 최근 날이 0). 끄는 동안에는 소수로 움직인다.
 */
@Composable
fun RecordPhotoPillars(
    month: YearMonth,
    posts: List<PostCalendarItem>,
    images: Map<String, ImageBitmap>,
    position: Animatable<Float, AnimationVector1D>,
    onSelectPost: (PostCalendarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val motion = remember { RecordPillarMotion() }
    val order = remember(month, posts) { RecordPhotoStackOrder(month, posts) }
    val count = order.entries.size
    val range = 0f..maxOf(0, count - 1).toFloat()

    DisposableEffect(motion) { onDispose { motion.stop() } }

    LaunchedEffect(position, motion) {
        snapshotFlow { position.value.roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                // 손으로 끌거나 관성으로 넘어갈 때만 낸다. 달력이나 탭으로 옮길 때는 내지 않는다.
                if (motion.isInteracting) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val width = maxWidth.value
        val pillarHeight = RecordPillarLayout.pillarHeight(stackHeight = maxHeight.value)
        val openSizes = order.entries.map { entry ->
            RecordPillarLayout.openSize(ratio = ratioOf(entry, images), pillarHeight = pillarHeight, width = width)
        }
        // 위치는 배치와 그리기 단계에서만 읽는다. 끄는 동안 기둥을 다시 구성하지 않고 자리와 폭만 다시 잡는다.
        val layouts = remember(count, width, openSizes, pillarHeight) {
            RecordPillarLayoutCache(count, width, openSizes, pillarHeight, motion)
        }
        val layout = { layouts.at(position.value) }
        // 고른 날. 날이 바뀔 때만 구성을 다시 한다.
        val focusedIndex by remember(range) {
            derivedStateOf {
                position.value
                    .coerceIn(range)
                    .roundToInt()
            }
        }

        val settle: (Float) -> Unit = { target ->
            motion.stop()
            scope.launch { position.animateTo(target, PillarSettleSpring) }
        }
        val emit: (Float) -> Unit = { raw ->
            motion.rawPosition = raw
            scope.launch { position.snapTo(RecordPillarLayout.detented(raw, count)) }
        }
        val currentSettle by rememberUpdatedState(settle)
        val currentEmit by rememberUpdatedState(emit)
        val currentRange by rememberUpdatedState(range)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(motion, position) {
                    val tracker = VelocityTracker()
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 관성으로 넘어가는 중에 다시 잡으면 걸림을 풀기 전 위치에서 이어 간다.
                            raw = motion.rawPosition ?: position.value
                            motion.stop()
                            motion.isInteracting = true
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val velocity = tracker
                                .calculateVelocity()
                                .x
                                .toDp()
                                .value / DRAG_STEP
                            val bounds = currentRange
                            if (raw !in bounds) {
                                // 끝을 넘겨 끌었으면 굴리지 않고 끝 날로 되돌아간다.
                                currentSettle(raw.coerceIn(bounds))
                            } else if (abs(velocity) <= MINIMUM_INERTIA_VELOCITY) {
                                currentSettle(raw.roundToInt().toFloat())
                            } else {
                                motion.isInteracting = true
                                motion.inertia = scope.launch {
                                    decelerate(raw, velocity, bounds, currentEmit)
                                    currentSettle(
                                        (motion.rawPosition ?: raw)
                                            .coerceIn(bounds)
                                            .roundToInt()
                                            .toFloat(),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            currentSettle(
                                raw
                                    .coerceIn(currentRange)
                                    .roundToInt()
                                    .toFloat(),
                            )
                        },
                    ) { change, dragAmount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // 순서는 최근 날이 0이고 최근 날이 오른쪽에 있다. 스크롤처럼 오른쪽으로 끌면 지난 날(왼쪽)로 가므로 순서가 커진다.
                        raw += dragAmount.toDp().value / DRAG_STEP
                        change.consume()
                        currentEmit(RecordPhotoStackOrder.rubberBanded(raw, currentRange))
                    }
                },
        ) {
            order.entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    val isFocused = index == focusedIndex
                    RecordPillar(
                        entry = entry,
                        index = index,
                        image = entry.post?.let { images[it.postId] },
                        isFocused = isFocused,
                        layout = layout,
                        distance = { abs(index - position.value.coerceIn(range)) },
                        onClick = {
                            val post = entry.post
                            if (isFocused && post != null) {
                                onSelectPost(post)
                            } else {
                                settle(index.toFloat())
                            }
                        },
                    )
                }
            }

            val markerColor = ChalkakTheme.colors.inputCursor
            Canvas(
                modifier = Modifier
                    .offset {
                        IntOffset((layout().focusX - MARKER_WIDTH / 2).dp.roundToPx(), MARKER_TOP.dp.roundToPx())
                    }.size(MARKER_WIDTH.dp, MARKER_HEIGHT.dp),
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
                drawPath(path, markerColor)
            }
        }
    }
}

/**
 * 기둥 하나와 그 아래 눈금. 자리·폭·모서리는 [layout]과 [distance]를 배치·그리기 단계에서 읽어,
 * 끄는 동안 다시 구성하지 않는다.
 */
@Composable
private fun RecordPillar(
    entry: RecordPhotoStackEntry,
    index: Int,
    image: ImageBitmap?,
    isFocused: Boolean,
    layout: () -> RecordPillarLayout,
    distance: () -> Float,
    onClick: () -> Unit,
) {
    val post = entry.post
    val date = entry.date.format(RecordStackFormat.monthDay)
    val showsEmptyLabel by remember(index) {
        derivedStateOf { layout().widths.getOrElse(index) { 0f } > EMPTY_LABEL_MINIMUM_WIDTH }
    }

    val pillarModifier = Modifier
        .offset {
            val current = layout()
            IntOffset((current.centers[index] - current.widths[index] / 2).dp.roundToPx(), PILLAR_TOP.dp.roundToPx())
        }.layout { measurable, _ ->
            val current = layout()
            val placeable = measurable.measure(
                Constraints.fixed(
                    current.widths[index]
                        .dp
                        .roundToPx(),
                    current.pillarHeight.dp
                        .roundToPx(),
                ),
            )
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }.graphicsLayer {
            val d = distance()
            val radius = if (d < 1f) FOCUS_RADIUS + (PILLAR_RADIUS - FOCUS_RADIUS) * d else PILLAR_RADIUS
            shape = RoundedCornerShape(radius.dp)
            clip = true
        }.semantics(mergeDescendants = true) {
            contentDescription = if (post == null) "$date, 기록 없음" else "$date 사진"
            selected = isFocused
        }.clickable(
            interactionSource = null,
            indication = null,
            role = Role.Button,
            onClickLabel = if (isFocused && post != null) "피드에서 보기" else null,
            onClick = onClick,
        )

    if (post != null) {
        // 피드가 이 사진에서 확대되어 열리고, 닫히면 이 사진으로 돌아온다. 고른 날만 출발 자리로 둔다.
        // 기둥은 가로로 긴 사진의 양옆을 잘라 보이므로, 전환도 같은 자르기와 사진 비율로 시작해야 모양이 튀지 않는다.
        val photoSource = if (isFocused) {
            rememberSharedPhotoSource(
                key = PhotoTransitionKey(post.postId, recordTransitionSourceId(post.postId)),
                imageModel = post.thumbnailImageUrl,
                signatureModel = null,
                aspectRatio = image?.let { aspectRatioForSize(it.width, it.height) },
                contentScale = ContentScale.Crop,
                sourceShape = FocusedPillarShape,
            )
        } else {
            null
        }
        RecordPhotoImage(
            image = image,
            modifier = pillarModifier.then(photoSource?.modifier ?: Modifier),
        )
    } else {
        RecordEmptyFrame(
            day = entry.day.takeIf { showsEmptyLabel },
            modifier = pillarModifier,
        )
    }

    RecordPillarTick(
        entry = entry,
        isFocused = isFocused,
        modifier = Modifier
            .offset {
                val current = layout()
                IntOffset(
                    (current.centers[index] - TICK_SLOT_WIDTH / 2).dp.roundToPx(),
                    (PILLAR_TOP + current.pillarHeight + TICK_TOP_SPACING).dp.roundToPx(),
                )
            }.width(TICK_SLOT_WIDTH.dp),
    )
}

/**
 * 위치마다 기둥 줄을 한 번만 계산한다. 같은 프레임에 기둥마다 줄을 다시 묻기 때문이다.
 * 줄을 민 거리는 다음 계산에 이어 쓰도록 [motion]에 남긴다.
 */
private class RecordPillarLayoutCache(
    private val count: Int,
    private val width: Float,
    private val openSizes: List<Float>,
    private val pillarHeight: Float,
    private val motion: RecordPillarMotion,
) {
    private var lastPosition = Float.NaN
    private var last: RecordPillarLayout? = null

    fun at(position: Float): RecordPillarLayout {
        last?.takeIf { position == lastPosition }?.let { return it }
        val layout = RecordPillarLayout(
            entryCount = count,
            position = position,
            width = width,
            previousShift = motion.rowShift,
            openSizes = openSizes,
            pillarHeight = pillarHeight,
        )
        // 줄이 화면보다 길 때 지금 밀려 있는 거리. 펼친 기둥이 가장자리에 닿을 때만 바뀐다.
        motion.rowShift = layout.shift
        lastPosition = position
        last = layout
        return layout
    }
}

private val FocusedPillarShape = RoundedCornerShape(FOCUS_RADIUS.dp)

/** 날마다 눈금 하나. 일요일은 길게, 5일마다(그리고 1일) 숫자를 붙인다. 고른 날은 늘 숫자를 진하게 적는다. */
@Composable
private fun RecordPillarTick(
    entry: RecordPhotoStackEntry,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ChalkakTheme.colors
    val isSunday = entry.date.isSunday()
    val showsNumber = isFocused || entry.day % 5 == 0 || entry.day == 1
    Box(modifier = modifier.height(TICK_AREA_HEIGHT.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(TICK_WIDTH.dp, if (isSunday) SUNDAY_TICK_HEIGHT.dp else TICK_HEIGHT.dp)
                .background(if (isSunday) colors.textSecondary else colors.calendarCellBorder),
        )
        if (showsNumber) {
            // 눈금 길이와 상관없이 숫자는 한 줄에 맞춘다.
            Text(
                text = "${entry.day}",
                color = if (isFocused) colors.textPrimary else colors.textMuted,
                style = ChalkakTheme.typography.caption
                    .copy(fontFeatureSettings = TABULAR_NUMBERS),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (SUNDAY_TICK_HEIGHT + TICK_NUMBER_SPACING).dp)
                    .wrapContentWidth(unbounded = true),
            )
        }
    }
}

/** 피드 전환이 출발하는 자리의 이름. 기둥과 책자가 같은 이름을 쓴다(한 번에 하나만 보인다). */
internal fun recordTransitionSourceId(postId: String): String = "record:$postId"

/** 사진의 가로/세로 비율. 기록이 없는 날은 세로 4 : 가로 3 프레임이고, 사진을 아직 받지 못했으면 null이다. */
private fun ratioOf(
    entry: RecordPhotoStackEntry,
    images: Map<String, ImageBitmap>,
): Float? {
    val post = entry.post ?: return EMPTY_RATIO
    val image = images[post.postId] ?: return null
    if (image.width <= 0 || image.height <= 0) return null
    return image.width.toFloat() / image.height
}

/**
 * 사진 보정 눈금자처럼 손을 뗀 속도에서 감속하며 하루씩 넘어간다. 끝 날에 닿거나 충분히 느려지면 멈춘다.
 *
 * @param velocity 초당 넘어가는 날 수.
 */
private suspend fun decelerate(
    from: Float,
    velocity: Float,
    range: ClosedFloatingPointRange<Float>,
    emit: (Float) -> Unit,
) {
    var raw = from
    var speed = velocity
    var lastFrame = withFrameNanos { it }
    while (currentCoroutineContext().isActive && abs(speed) > MINIMUM_INERTIA_VELOCITY) {
        val now = withFrameNanos { it }
        val elapsed = (now - lastFrame) / 1_000_000_000f
        lastFrame = now
        raw += speed * elapsed
        speed *= DECELERATION_RATE.pow(elapsed * 1000)
        val clamped = raw.coerceIn(range)
        emit(clamped)
        // 끝 날에 닿으면 더 가지 않는다.
        if (clamped != raw) break
    }
}

/**
 * 기둥 줄의 너비와 가로 위치(dp).
 * - 1일이 왼쪽 끝, 가장 최근 날(순서 0)이 오른쪽 끝이다.
 * - 한 달이 화면에 들어올 수 있으면 가는 기둥을 8dp까지 좁혀 1일부터 오늘까지 다 보이게 하고, 1일을 왼쪽 끝에 붙인다.
 * - 그래도 넘치면 펼친 기둥이 가장자리 가까이 갈 때만 줄을 민다([previousShift]를 이어 쓴다).
 * - 끝을 넘겨 끌면 기둥 폭은 끝 그대로 두고 줄 전체가 손가락을 따라 밀린다.
 * - 펼친 기둥은 날마다 [openSizes]의 폭(사진 원본 비율)으로 펼쳐지고, 멀어질수록 가는 기둥으로 줄어든다.
 */
class RecordPillarLayout(
    entryCount: Int,
    position: Float,
    val width: Float,
    previousShift: Float?,
    openSizes: List<Float> = emptyList(),
    val pillarHeight: Float = DEFAULT_PILLAR_HEIGHT,
) {
    val widths: List<Float>
    val centers: List<Float>

    /** 고른 자리의 가로 위치. 삼각 표시가 여기를 가리킨다. */
    val focusX: Float

    /** 줄을 민 거리. 끝을 넘겨 끈 고무줄 거리는 넣지 않는다. */
    val shift: Float

    init {
        val count = entryCount
        val last = maxOf(0, count - 1).toFloat()
        val clamped = position.coerceIn(0f, last)
        val thin = thinWidth(count, width)
        val overscroll = -(position - clamped) * (thin + GAP)

        val widths = MutableList(count) { thin }
        val rawCenters = MutableList(count) { 0f }
        var x = 0f
        for (index in count - 1 downTo 0) {
            val distance = abs(index - clamped)
            val open = openSizes.getOrNull(index) ?: openSize(ratio = null, pillarHeight = pillarHeight)
            val w = when {
                distance < 1f -> open + (NEAR_WIDTH - open) * distance
                distance < 2f -> NEAR_WIDTH + (thin - NEAR_WIDTH) * (distance - 1)
                else -> thin
            }
            widths[index] = w
            rawCenters[index] = x + w / 2
            x += w + GAP
        }

        val lower = floor(clamped).toInt()
        val upper = minOf(maxOf(0, count - 1), lower + 1)
        val fraction = clamped - lower
        val focus = if (rawCenters.isEmpty()) {
            0f
        } else {
            rawCenters[lower] +
                (rawCenters[upper] - rawCenters[lower]) * fraction
        }
        val row = maxOf(0f, x - GAP)
        val leading = EDGE_PADDING
        val trailing = width - EDGE_PADDING - row

        shift = if (trailing >= leading) {
            leading
        } else {
            val focusWidth = if (rawCenters.isEmpty()) OPEN_WIDTH else maxOf(widths[lower], widths[upper])
            val edge = focusWidth / 2 + SCROLL_EDGE_MARGIN
            var next = previousShift ?: (width / 2 - focus)
            if (focus + next < edge) next = edge - focus
            if (focus + next > width - edge) next = width - edge - focus
            next.coerceIn(trailing, leading)
        }

        this.widths = widths
        this.centers = rawCenters.map { it + shift + overscroll }
        this.focusX = focus + shift + overscroll
    }

    companion object {
        /** 기본 묶음 높이일 때의 기둥 높이. */
        const val DEFAULT_PILLAR_HEIGHT = PILLAR_HEIGHT

        /**
         * 묶음 높이에서 기둥 높이를 정한다. 기둥 위아래(버튼, 눈금, 아래 여백)는 높이가 그대로이고 남는 만큼 기둥이 커진다.
         * 예: 기본 묶음 높이 300 → 126, 410 → 236.
         */
        fun pillarHeight(stackHeight: Float): Float = maxOf(
            PILLAR_HEIGHT,
            stackHeight - (RecordStackMetrics.Height.value - PILLAR_HEIGHT),
        )

        /**
         * 펼친 기둥 폭. 높이는 늘 기둥 높이이고, 폭을 사진 비율대로 잡는다.
         * 폭이 최대 폭을 넘는 넓은 사진은 최대 폭에서 멈추고 양옆이 잘린다.
         * 최대 폭은 기본 240dp를 기둥이 커진 만큼 키우되, 옆 기둥이 보일 자리([OPEN_SIDE_ROOM])는 남긴다.
         * 예(기둥 126): 4:3 → 168, 3:4 → 94.5, 3:1 → 240(양옆 잘림). 비율이 없으면(사진을 받기 전) 150.
         */
        fun openSize(
            ratio: Float?,
            pillarHeight: Float = DEFAULT_PILLAR_HEIGHT,
            width: Float = Float.POSITIVE_INFINITY,
        ): Float {
            val scale = pillarHeight / PILLAR_HEIGHT
            if (ratio == null || ratio <= 0f) return OPEN_WIDTH * scale
            val maxWidth = maxOf(MAX_OPEN_WIDTH, minOf(MAX_OPEN_WIDTH * scale, width - OPEN_SIDE_ROOM))
            return minOf(pillarHeight * ratio, maxWidth)
        }

        /** 가는 기둥 폭. 펼친 기둥과 바로 옆 두 기둥을 빼고 남은 자리에 한 달이 다 들어가게 8~12dp 안에서 정한다. */
        fun thinWidth(
            entryCount: Int,
            width: Float,
        ): Float {
            val thinCount = entryCount - 3
            if (thinCount <= 0) return FAR_WIDTH
            val room = width - EDGE_PADDING * 2 - OPEN_WIDTH - NEAR_WIDTH * 2 - GAP * (entryCount - 1)
            return (room / thinCount).coerceIn(MIN_FAR_WIDTH, FAR_WIDTH)
        }

        /**
         * 끄는 위치를 하루마다 걸리게 바꾼다. 날짜 가까이(앞뒤 [DETENT_HALF_WIDTH])에서는 그 날에 멈춰 있다가,
         * 그 사이 구간에서만 다음 날로 넘어간다. 범위 밖(끝을 넘겨 끈 고무줄)은 그대로 둔다.
         * 예: 3.2 → 3, 3.5 → 3.5, 3.8 → 4.
         */
        fun detented(
            raw: Float,
            entryCount: Int,
        ): Float {
            val last = maxOf(0, entryCount - 1).toFloat()
            if (raw <= 0f || raw >= last) return raw
            val day = floor(raw)
            val fraction = raw - day
            val hold = DETENT_HALF_WIDTH
            return when {
                fraction <= hold -> day
                fraction >= 1 - hold -> day + 1
                else -> day + (fraction - hold) / (1 - hold * 2)
            }
        }
    }
}

/** 끌기와 관성 상태. 바뀌어도 다시 그릴 필요가 없어 스냅샷 상태로 두지 않는다. */
private class RecordPillarMotion {
    /** 걸림을 풀기 전 위치. 관성 중에 다시 잡으면 여기서 이어 간다. */
    var rawPosition: Float? = null

    /** 손으로 끄는 중이거나 관성으로 넘어가는 중이다. 이때만 햅틱을 낸다. */
    var isInteracting = false
    var inertia: Job? = null
    var rowShift: Float? = null

    fun stop() {
        inertia?.cancel()
        inertia = null
        rawPosition = null
        isInteracting = false
    }
}

/** 0.38초 안에 붙는 스프링. 강성은 (2π / 0.38)²이다. */
private val PillarSettleSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 273f)

private const val PILLAR_HEIGHT = 126f

/** 위 버튼 줄 아래 묶음 공통 간격만큼 띄워 시작한다. */
private const val PILLAR_TOP = RecordStackMetrics.CONTENT_TOP

/** 비율을 모를 때(사진을 받기 전) 펼친 기둥 폭. */
private const val OPEN_WIDTH = 150f

/** 넓은 사진을 펼칠 때 넘지 않는 폭. 이보다 넓으면 양옆을 자른다. */
private const val MAX_OPEN_WIDTH = 240f

/** 기둥이 커져도 펼친 사진 옆에 남기는 폭. 양쪽 여백과 옆 기둥 한두 개가 보이게 한다. */
private const val OPEN_SIDE_ROOM = 72f
private const val NEAR_WIDTH = 26f
private const val FAR_WIDTH = 12f
private const val MIN_FAR_WIDTH = 8f
private const val GAP = 6f
private const val EDGE_PADDING = 20f

/** 줄이 넘칠 때 펼친 기둥이 가장자리에서 이만큼(펼친 폭의 절반 + 여유) 안쪽에 머문다. */
private const val SCROLL_EDGE_MARGIN = 24f
private const val FOCUS_RADIUS = 16f
private const val PILLAR_RADIUS = 6f

/** 기록이 없는 날을 펼친 프레임 비율(가로/세로). 세로 4 : 가로 3이다. */
private const val EMPTY_RATIO = 3f / 4f
private const val EMPTY_LABEL_MINIMUM_WIDTH = 40f
private const val TICK_TOP_SPACING = 6f
private const val TICK_WIDTH = 1f
private const val TICK_HEIGHT = 7f
private const val SUNDAY_TICK_HEIGHT = 13f
private const val TICK_NUMBER_SPACING = 3f
private const val TICK_AREA_HEIGHT = 30f

/** 눈금과 숫자를 그리는 칸 폭. 두 자리 숫자가 들어간다. */
private const val TICK_SLOT_WIDTH = 24f

/** 삼각 표시는 기둥 바로 위(9dp)에 둔다. */
private const val MARKER_HEIGHT = 8f
private const val MARKER_WIDTH = 12f
private const val MARKER_TOP = PILLAR_TOP - 9f - MARKER_HEIGHT / 2

/** 하루를 넘기는 데 필요한 손가락 거리. 가는 기둥 한 칸보다 넉넉히 두어 천천히 넘어간다. */
private const val DRAG_STEP = 32f

/** 날짜 앞뒤로 멈춰 있는 구간(하루의 비율). 하루 32dp 중 앞뒤 8dp씩 걸려 있다. */
private const val DETENT_HALF_WIDTH = 0.25f

/** 1ms마다 속도에 곱하는 비율(손을 뗀 속도로 약 0.2초 간 만큼 더 넘어간다). */
private const val DECELERATION_RATE = 0.995f

/** 초당 넘어가는 날 수. 이보다 느리면 관성을 멈추고 가까운 날에 붙는다. */
private const val MINIMUM_INERTIA_VELOCITY = 1.5f

@Preview(showBackground = true, widthDp = 393, heightDp = 300)
@Composable
private fun RecordPhotoPillarsPreview() {
    val month = YearMonth.of(2026, 8)
    val posts = listOf(2, 5, 9, 14, 20).map { day ->
        PostCalendarItem(
            postId = "preview-$day",
            topicDate = month.atDay(day),
            thumbnailImageUrl = "",
            status = PostStatus.APPROVED,
        )
    }
    ChalkakTheme {
        RecordPhotoPillars(
            month = month,
            posts = posts,
            images = emptyMap(),
            position = remember { Animatable(0f) },
            onSelectPost = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(RecordStackMetrics.Height),
        )
    }
}
