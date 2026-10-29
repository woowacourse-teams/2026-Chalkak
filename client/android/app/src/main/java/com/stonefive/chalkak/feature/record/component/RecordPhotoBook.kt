package com.stonefive.chalkak.feature.record.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.LocalSharedTransitionScope
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.aspectRatioForSize
import com.stonefive.chalkak.core.designsystem.component.image.rememberSharedPhotoSource
import com.stonefive.chalkak.core.designsystem.component.logo.ChalkakLogo
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 월간 상단의 사진 묶음(월간 책자). 한 달이 천 장정 하드커버 한 권이다.
 * - 정면으로 세워 펼친다. 왼쪽 면에 요일·날짜·주제, 오른쪽 면에 사진을 둔다. 책 양 끝의 화살표로도 넘긴다.
 * - 사진은 원래 비율 그대로 넣고 자르지 않는다.
 * - 화면 왼쪽 절반을 누르거나 오른쪽으로 끌면 전날, 오른쪽 절반을 누르거나 왼쪽으로 끌면 다음 날로 넘어간다.
 * - 사진을 누르면 그 사진의 피드로 간다.
 * - 속표지에는 찰캌 로고, "N월의 기록", 내 사인을 넣는다.
 *
 * 위치([position])는 다른 모양과 같이 보고 있는 날의 순서다(가장 최근 날이 0).
 * 책 안에서는 넘긴 장 수(spread)로 다룬다. 0은 덮인 표지, 1은 속표지, d + 1은 d일이다.
 *
 * @param clothColor 표지 천 색. 그 달 첫 사진의 대표색이며, 모르면 null이다.
 * @param signatureUrl 속표지에 넣을 내 사인. 아직 받지 못했거나 없으면 비워 둔다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecordPhotoBook(
    month: YearMonth,
    posts: List<PostCalendarItem>,
    images: Map<String, ImageBitmap>,
    topicTitles: Map<LocalDate, String>,
    position: Animatable<Float, AnimationVector1D>,
    onSelectPost: (PostCalendarItem) -> Unit,
    modifier: Modifier = Modifier,
    clothColor: Color? = null,
    signatureUrl: String? = null,
    today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
) {
    val scope = rememberCoroutineScope()
    val model = remember(month, posts, today) { RecordBookModel(month, posts, today) }
    val geometry = RecordBookGeometry
    val bend = remember { RecordBookBend() }

    // 넘기는 방향이 바뀌었는지 보고 휘는 방향을 천천히 뒤집는다.
    // 손가락이 조금 떨리는 정도로는 바꾸지 않고, 가장 멀리 간 자리에서 BEND_FLIP_THRESHOLD 이상 돌아와야 바꾼다.
    LaunchedEffect(position, model) {
        snapshotFlow { model.spread(position.value) }.collect { spread ->
            val anchor = bend.anchor ?: spread
            if ((spread - anchor) * bend.target >= 0) {
                bend.anchor = spread
            } else if (abs(spread - anchor) > BEND_FLIP_THRESHOLD) {
                bend.anchor = spread
                bend.target = -bend.target
                launch { bend.direction.animateTo(bend.target, tween(BEND_FLIP_MILLIS)) }
            }
        }
    }

    val turn: (Int) -> Unit = { pages ->
        // 순서는 최근 날이 0이므로, 다음 날(장을 넘김)은 순서를 줄인다.
        val target = (position.value.roundToInt() - pages).coerceIn(0, model.maxSpread).toFloat()
        scope.launch { position.animateTo(target, turnSpring(abs(target - position.value))) }
    }
    val currentTurn by rememberUpdatedState(turn)

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.clipToBounds(),
    ) {
        val screenWidth = maxWidth.value
        // 기본 높이에서는 화면 폭에 맞춘 크기로 그린다. 묶음이 위아래로 커지면 책도 같은 비율로 커지되,
        // 책이 화면 양옆을 넘지 않는 데까지만 커진다.
        val fitScale = minOf(1f, screenWidth / STAGE_WIDTH)
        val growth = minOf(
            maxOf(1f, maxHeight.value / RecordStackMetrics.Height.value),
            geometry.maxGrowth(screenWidth / fitScale),
        )
        val scale = fitScale * growth
        // 책이 커지거나 줄어도 표지 위쪽이 늘 묶음 공통 높이(CONTENT_TOP)에 오게 옮긴다.
        val topOffset = RecordStackMetrics.CONTENT_TOP - (PAGE_TOP - COVER_MARGIN) * scale
        // 넘긴 장 수는 그리기 단계에서만 읽는다. 구성은 펼친 자리나 멈춤 여부가 바뀔 때만 다시 한다.
        val spread = { model.spread(position.value) }
        val settled by remember(model) { derivedStateOf { spread().roundToInt() } }
        val isSettled by remember(
            model,
        ) { derivedStateOf { abs(spread() - spread().roundToInt()) < SETTLED_TOLERANCE } }
        val cover = clothColor?.let(::clothOf) ?: ChalkakTheme.colors.calendarCellBorder
        val dragStep = DRAG_STEP * scale
        val maxSpread = model.maxSpread
        val settledPost = model.post(settled)?.takeIf { isSettled }
        // 피드로 오가는 동안에는 책에 그린 사진을 숨긴다. 움직이는 사진과 제자리 사진이 겹쳐 두 장으로 보이지 않게 한다.
        // 그리기 단계에서만 읽어, 전환이 시작될 때 책 전체를 다시 구성하느라 첫 프레임이 멈추지 않게 한다.
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val photoTransitionCoordinator = LocalPhotoTransitionCoordinator.current
        val hiddenPostId = remember(model, position, sharedTransitionScope, photoTransitionCoordinator) {
            derivedStateOf {
                model.post(model.spread(position.value).roundToInt())?.postId?.takeIf {
                    sharedTransitionScope?.isTransitionActive == true &&
                        photoTransitionCoordinator.selectedSnapshot(it) != null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(position, model, dragStep) {
                    val tracker = VelocityTracker()
                    var start = 0f
                    var translation = 0f
                    val range = 0f..maxSpread.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = position.value
                            translation = 0f
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val velocity = tracker
                                .calculateVelocity()
                                .x
                                .toDp()
                                .value
                            val projected = start + (translation + velocity * FLING_PROJECTION_SECONDS) / dragStep
                            // 세게 튕겨도 한 번에 두 장까지만 넘긴다.
                            val base = start.roundToInt().toFloat()
                            val limited = projected.coerceIn(base - MAX_FLING, base + MAX_FLING)
                            val target = limited
                                .roundToInt()
                                .toFloat()
                                .coerceIn(range)
                            scope.launch { position.animateTo(target, turnSpring(abs(target - position.value))) }
                        },
                        onDragCancel = {
                            val target = position.value
                                .roundToInt()
                                .toFloat()
                                .coerceIn(range)
                            scope.launch { position.animateTo(target, turnSpring(abs(target - position.value))) }
                        },
                    ) { change, dragAmount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        // 장 하나 폭만큼 끌면 한 장 넘어간다. 오른쪽으로 끌면 전날(순서가 커진다).
                        translation += dragAmount.toDp().value
                        val next = RecordPhotoStackOrder.rubberBanded(start + translation / dragStep, range)
                        scope.launch { position.snapTo(next) }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .requiredSize(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = topOffset.dp.toPx()
                    },
            ) {
                RecordBookStage(
                    spread = spread,
                    model = model,
                    images = images,
                    topicTitles = topicTitles,
                    signatureUrl = signatureUrl,
                    coverColor = cover,
                    hiddenPostId = { hiddenPostId.value },
                    bendDirection = { bend.direction.value },
                )
                RecordBookTouchLayer(
                    model = model,
                    settled = settled,
                    onTurn = { currentTurn(it) },
                )
            }

            // 펼친 날의 사진 자리. 배율이 걸린 무대 밖 화면 좌표에 두어, 피드가 보이는 크기 그대로의 자리에서 열리고 돌아온다.
            settledPost?.let { post ->
                val frame = geometry.photoFrame(RecordPhotoAspect.widthRatio(images[post.postId]))
                RecordBookPhotoSource(
                    post = post,
                    image = images[post.postId],
                    edgeWidth = (PHOTO_EDGE_WIDTH * scale).dp,
                    onClick = { onSelectPost(post) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (screenWidth / 2 + (frame.left - STAGE_WIDTH / 2) * scale).dp,
                            y = (frame.top * scale + topOffset).dp,
                        ).size((frame.width * scale).dp, (frame.height * scale).dp),
                )
            }

            // 화살표는 책과 함께 커지지 않고 화면 기준으로 둔다. 책이 커져도 화면 양 끝에 늘 보인다.
            val centerY = (PAGE_TOP + PAGE_HEIGHT / 2) * scale + topOffset
            val arrowX = maxOf(
                ARROW_EDGE_INSET + ARROW_TOUCH / 2,
                screenWidth / 2 + (geometry.arrowCenterX() - STAGE_WIDTH / 2) * scale,
            )
            RecordFloatingIconButton(
                title = "전날 보기",
                iconRes = R.drawable.ic_display_arrow_left,
                onClick = { turn(-1) },
                enabled = settled > 0,
                diameter = ARROW_DIAMETER.dp,
                iconSize = ARROW_ICON_SIZE.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (arrowX - ARROW_TOUCH / 2).dp, y = (centerY - ARROW_TOUCH / 2).dp),
            )
            RecordFloatingIconButton(
                title = "다음 날 보기",
                iconRes = R.drawable.ic_display_arrow_right,
                onClick = { turn(1) },
                enabled = settled < maxSpread,
                diameter = ARROW_DIAMETER.dp,
                iconSize = ARROW_ICON_SIZE.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (screenWidth - arrowX - ARROW_TOUCH / 2).dp, y = (centerY - ARROW_TOUCH / 2).dp),
            )
        }
    }
}

/** 그려진 책 위에서 누를 자리. 책 무대 좌표로 놓인다. */
@Composable
private fun BoxScope.RecordBookTouchLayer(
    model: RecordBookModel,
    settled: Int,
    onTurn: (Int) -> Unit,
) {
    // 화면 왼쪽 절반은 전날, 오른쪽 절반은 다음 날로 넘어간다.
    Box(
        modifier = Modifier
            .matchParentSize()
            .semantics {
                contentDescription = model.accessibilityLabel(settled)
                customActions = listOf(
                    CustomAccessibilityAction("전날 보기") {
                        onTurn(-1)
                        true
                    },
                    CustomAccessibilityAction("다음 날 보기") {
                        onTurn(1)
                        true
                    },
                )
            }.pointerInput(onTurn) {
                detectTapGestures { offset ->
                    onTurn(if (offset.x < size.width / 2f) -1 else 1)
                }
            },
    )
}

/**
 * 펼친 날 오른쪽 면의 사진. 책에 그린 사진과 같은 자리·크기로 위에 겹쳐 그린다.
 * 피드가 이 사진에서 확대되어 열리고, 닫히면 이 사진으로 줄어들며 돌아온다.
 * 돌아올 때 움직이는 것이 이 사진이라, 사진을 직접 그려야 전환이 보인다.
 *
 * @param edgeWidth 책에 그린 사진과 같은 두께로 두르는 가장자리 선. 화면에 보이는 두께다.
 */
@Composable
private fun RecordBookPhotoSource(
    post: PostCalendarItem,
    image: ImageBitmap?,
    edgeWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 피드가 처음부터 사진 비율로 자리를 잡아, 전환 도중 도착 자리가 바뀌지 않게 한다.
    val photoSource = rememberSharedPhotoSource(
        key = PhotoTransitionKey(post.postId, recordTransitionSourceId(post.postId)),
        imageModel = post.thumbnailImageUrl,
        signatureModel = null,
        aspectRatio = image?.let { aspectRatioForSize(it.width, it.height) },
        sourceShape = RectangleShape,
    )
    RecordPhotoImage(
        image = image,
        modifier = modifier
            .then(photoSource.modifier)
            .border(edgeWidth, Ink.copy(alpha = PHOTO_EDGE_ALPHA))
            .semantics {
                contentDescription = "${post.topicDate.format(RecordStackFormat.monthDay)} 사진"
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = "피드에서 보기",
                onClick = onClick,
            ),
    )
}

/**
 * 책 무대. 넘기는 동안에는 다시 구성하지 않고 그리기만 다시 한다.
 * 그 달의 낱장은 처음에 한 번 다 구성해 두고, 넘긴 장 수([spread])는 그리기와 배치 단계에서만 읽어 보이는 장만 그린다.
 * 그래서 달력에서 먼 날을 골라 여러 장을 한꺼번에 넘겨도 프레임마다 낱장을 새로 만들지 않는다.
 */
@Composable
private fun RecordBookStage(
    spread: () -> Float,
    model: RecordBookModel,
    images: Map<String, ImageBitmap>,
    topicTitles: Map<LocalDate, String>,
    signatureUrl: String?,
    coverColor: Color,
    hiddenPostId: () -> String?,
    bendDirection: () -> Float,
) {
    val maxSpread = model.maxSpread.toFloat()
    val settled = { spread().coerceIn(0f, maxSpread) }
    val faces = RecordBookFaces(model, images, topicTitles, signatureUrl, coverColor, hiddenPostId)

    Box(
        modifier = Modifier
            .size(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
            .graphicsLayer {
                // 덮였을 때는 표지 가운데가 책등 자리에 오게 옮긴다. 끝을 넘겨 끌면 책 전체가 조금 밀린다.
                val current = spread()
                val clamped = current.coerceIn(0f, maxSpread)
                val open = minOf(1f, clamped)
                translationX = (-(1 - open) * COVER_WIDTH / 2 - (current - clamped) * OVERSCROLL_SHIFT).dp.toPx()
            },
    ) {
        RecordBookBase(open = { minOf(1f, settled()) }, coverColor = coverColor)

        for (leafIndex in 0..model.maxSpread) {
            key(leafIndex) {
                RecordLeaf(
                    index = leafIndex,
                    turn = { (settled() - leafIndex).coerceIn(0f, 1f) },
                    // 표지와 펼친 자리 앞뒤 두 장만 보인다. 나머지는 위에 쌓인 장에 가려진다.
                    isVisible = { leafIndex == 0 || abs(leafIndex - floor(settled()).toInt()) <= 2 },
                    direction = bendDirection,
                    faces = faces,
                )
            }
        }

        RecordTurnShadow(settled = settled)
    }
}

/** 책 그림자, 책등, 뒤표지. 정면에서 보는 책이라 종이 다발의 두께 면은 그리지 않는다. */
@Composable
private fun RecordBookBase(
    open: () -> Float,
    coverColor: Color,
) {
    val coverShape = RoundedCornerShape(
        topStart = COVER_INNER_RADIUS.dp,
        bottomStart = COVER_INNER_RADIUS.dp,
        topEnd = COVER_RADIUS.dp,
        bottomEnd = COVER_RADIUS.dp,
    )
    // 펼칠수록 왼쪽으로 그림자가 넓어진다.
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (SPINE_X - COVER_WIDTH * open()).dp.roundToPx(),
                    (PAGE_TOP - COVER_MARGIN).dp.roundToPx(),
                )
            }.layout { measurable, _ ->
                val width = (COVER_WIDTH * (1 + open())).dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, COVER_HEIGHT.dp.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }.shadow(DESK_SHADOW_ELEVATION.dp, RoundedCornerShape(SHADOW_RADIUS.dp), clip = false)
            .background(coverColor, RoundedCornerShape(SHADOW_RADIUS.dp)),
    )
    Box(
        modifier = Modifier
            .offset(x = (SPINE_X - SPINE_WIDTH / 2).dp, y = (PAGE_TOP - COVER_MARGIN).dp)
            .size(SPINE_WIDTH.dp, COVER_HEIGHT.dp)
            .background(lerp(coverColor, Ink, SPINE_DARKEN), RoundedCornerShape(SPINE_RADIUS.dp)),
    )
    Box(
        modifier = Modifier
            .offset(x = SPINE_X.dp, y = (PAGE_TOP - COVER_MARGIN).dp)
            .size(COVER_WIDTH.dp, COVER_HEIGHT.dp)
            .background(coverColor, coverShape),
    )
}

/**
 * 책등에 붙은 낱장 한 장. 펼침면 번호보다 앞선 장은 왼쪽으로 넘어가 있고, 그 사이에서는 책등을 축으로 넘어간다.
 * - 앞뒷면은 한 번만 그려 녹화해 두고, 넘기는 동안에는 녹화본을 돌리거나 휘어 그리기만 한다.
 * - 넘어가는 중인 종이는 세로 띠로 나눠 띠마다 기울기를 달리 주어 휜 종이처럼 그린다.
 *   띠마다 앞면이 보이는지 뒷면이 보이는지 따로 정하므로, 휜 종이의 한쪽은 앞면, 다른 쪽은 뒷면이 보일 수 있다.
 * - 하드커버 표지와 다 넘어간 장은 휘지 않는다.
 */
@Composable
private fun RecordLeaf(
    index: Int,
    turn: () -> Float,
    isVisible: () -> Boolean,
    direction: () -> Float,
    faces: RecordBookFaces,
) {
    val isCover = index == 0
    val width = if (isCover) COVER_WIDTH else PAGE_WIDTH
    val height = if (isCover) COVER_HEIGHT else PAGE_HEIGHT
    val top = if (isCover) PAGE_TOP - COVER_MARGIN else PAGE_TOP
    val shape = leafShape(isCover)
    val front = rememberGraphicsLayer()
    val back = rememberGraphicsLayer()
    // 넘어가는 장은 맨 위, 오른쪽에 누운 장은 앞 장이 위, 왼쪽에 누운 장은 뒤 장이 위에 온다.
    val zIndex by remember(index) {
        derivedStateOf {
            val current = turn()
            when {
                current > 0f && current < 1f -> TURNING_Z_INDEX
                current < 0.5f -> LEAF_Z_INDEX - index
                else -> LEAF_Z_INDEX + index
            }
        }
    }

    Box(
        modifier = Modifier
            .zIndex(zIndex)
            .offset(x = SPINE_X.dp, y = top.dp)
            .size(width.dp, height.dp),
    ) {
        // 앞뒷면은 화면에 바로 그리지 않고 녹화만 한다. 내용이 바뀔 때만 다시 녹화된다.
        Box(
            modifier = Modifier.drawWithContent {
                val outline = shape.createOutline(size, layoutDirection, this)
                front.record {
                    clipPath(Path().apply { addOutline(outline) }) { this@drawWithContent.drawContent() }
                }
            },
        ) { faces.Front(index) }
        Box(
            modifier = Modifier.drawWithContent {
                val outline = shape.createOutline(size, layoutDirection, this)
                back.record {
                    // 뒤집힌 면은 거울상이 되므로 미리 좌우를 바꿔 둔다.
                    scale(scaleX = -1f, scaleY = 1f) {
                        clipPath(Path().apply { addOutline(outline) }) { this@drawWithContent.drawContent() }
                    }
                }
            },
        ) { faces.Back(index) }

        // 넘기는 모습만 따로 다시 그린다. 자기 레이어를 두어 넘기는 동안 면을 다시 녹화하지 않는다.
        Spacer(
            modifier = Modifier
                .size(width.dp, height.dp)
                .graphicsLayer()
                .drawBehind {
                    if (!isVisible()) return@drawBehind
                    val current = turn()
                    if (!isCover && current > 0f && current < 1f) {
                        drawBentLeaf(front, back, current, direction())
                    } else {
                        drawRigidLeaf(front, back, current)
                    }
                },
        )
    }
}

/** 책등을 축으로 돌아가는 판판한 장. 넘어가는 동안에는 빛을 덜 받아 어두워진다. */
private fun DrawScope.drawRigidLeaf(
    front: GraphicsLayer,
    back: GraphicsLayer,
    turn: Float,
) {
    val layer = if (turn < 0.5f) front else back
    val shade = TURN_SHADE_ALPHA * sin(turn * PI.toFloat())
    layer.pivotOffset = Offset(0f, size.height / 2)
    layer.rotationY = -180f * turn
    layer.cameraDistance = LEAF_CAMERA_DISTANCE * density
    layer.colorFilter = if (shade > 0f) ColorFilter.tint(Ink.copy(alpha = shade), BlendMode.SrcAtop) else null
    drawLayer(layer)
}

/** 넘어가는 중인 종이. 녹화한 면을 띠마다 원근을 넣은 사영 변환으로 그린다. */
private fun DrawScope.drawBentLeaf(
    front: GraphicsLayer,
    back: GraphicsLayer,
    turn: Float,
    direction: Float,
) {
    val segments = RecordPaperCurl.segments(turn, direction, PAGE_WIDTH, PAGE_HEIGHT, PAPER_STRIPS)
    val stripWidth = PAGE_WIDTH / PAPER_STRIPS
    // 세워질수록 빛을 덜 받아 어두워진다. 종이 전체에 한 장의 그러데이션으로 칠한 뒤 띠로 나눠,
    // 겹쳐 그리는 띠 경계에서도 명암이 같아 줄이 보이지 않게 한다.
    val shadeStops = segments.mapIndexed { strip, segment ->
        strip.toFloat() / segments.size to Ink.copy(alpha = TURN_SHADE_ALPHA * sin(segment.startAngle))
    } + (1f to Ink.copy(alpha = TURN_SHADE_ALPHA * sin(segments.lastOrNull()?.endAngle ?: 0f)))
    val shade = Brush.horizontalGradient(*shadeStops.toTypedArray(), endX = size.width)
    listOf(front, back).forEach { layer ->
        layer.rotationY = 0f
        layer.colorFilter = null
    }
    val pxPerDp = density
    val matrix = android.graphics.Matrix()
    segments.forEachIndexed { strip, segment ->
        matrix.setValues(segment.matrixValues)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.scale(pxPerDp, pxPerDp)
            canvas.nativeCanvas.concat(matrix)
            canvas.nativeCanvas.scale(1 / pxPerDp, 1 / pxPerDp)
        }
        // 띠 사이에 틈이 보이지 않도록 조금씩 겹친다.
        clipRect(right = (stripWidth + PAPER_STRIP_OVERLAP) * pxPerDp) {
            translate(left = -strip * stripWidth * pxPerDp) {
                drawLayer(if (segment.showsFront) front else back)
                drawRect(shade)
            }
        }
        drawIntoCanvas { it.nativeCanvas.restore() }
    }
}

/** 넘어가는 장이 아래 면에 드리우는 그림자. 장 끝이 바닥에 비치는 자리까지 짙어진다. */
@Composable
private fun RecordTurnShadow(settled: () -> Float) {
    Spacer(
        modifier = Modifier
            .zIndex(TURNING_Z_INDEX - 1)
            .size(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
            .graphicsLayer()
            .drawBehind {
                val current = settled()
                val top = floor(current).toInt()
                val fraction = current - top
                if (fraction <= 0f || top < 1) return@drawBehind
                val isRight = fraction < 0.5f
                val strength = sin(fraction * PI.toFloat()) * CAST_SHADOW_ALPHA
                val edge = abs(cos(fraction * PI.toFloat())) * PAGE_WIDTH
                val pageWidth = PAGE_WIDTH.dp.toPx()
                val left = (if (isRight) SPINE_X else SPINE_X - PAGE_WIDTH).dp.toPx()
                val stops = arrayOf(
                    0f to Ink.copy(alpha = 0f),
                    maxOf(0f, (edge - 6) / PAGE_WIDTH) to Ink.copy(alpha = strength),
                    minOf(1f, (edge + 26) / PAGE_WIDTH) to Ink.copy(alpha = 0f),
                )
                // 왼쪽 면에서는 책등에서 바깥쪽으로 짙어지도록 그러데이션 방향을 뒤집는다.
                val brush = if (isRight) {
                    Brush.horizontalGradient(*stops, startX = left, endX = left + pageWidth)
                } else {
                    Brush.horizontalGradient(*stops, startX = left + pageWidth, endX = left)
                }
                drawRect(
                    brush = brush,
                    topLeft = Offset(left, PAGE_TOP.dp.toPx()),
                    size = Size(pageWidth, PAGE_HEIGHT.dp.toPx()),
                )
            },
    )
}

private fun leafShape(isCover: Boolean) = RoundedCornerShape(
    topStart = (if (isCover) COVER_INNER_RADIUS else 1f).dp,
    bottomStart = (if (isCover) COVER_INNER_RADIUS else 1f).dp,
    topEnd = (if (isCover) COVER_RADIUS else 2f).dp,
    bottomEnd = (if (isCover) COVER_RADIUS else 2f).dp,
)

/** 낱장의 앞뒷면. */
private class RecordBookFaces(
    val model: RecordBookModel,
    val images: Map<String, ImageBitmap>,
    val topicTitles: Map<LocalDate, String>,
    val signatureUrl: String?,
    val coverColor: Color,
    /** 피드로 오가는 동안 비워 둘 사진. 그동안은 위에 겹친 사진 자리가 대신 움직인다. 그리기 단계에서만 읽는다. */
    val hiddenPostId: () -> String?,
) {
    @Composable
    fun Front(index: Int) {
        val day = model.day(index)
        when {
            index == 0 -> CoverFront(model, images, coverColor)
            index == 1 -> TitlePage(model, signatureUrl)
            day != null -> PhotoPage(day, images, hiddenPostId)
            else -> Paper(isRight = true)
        }
    }

    /** 낱장 뒷면. 다음 날의 왼쪽 면이고, 마지막 장 뒤는 빈 종이다. */
    @Composable
    fun Back(index: Int) {
        val day = model.day(index + 1)
        when {
            index == 0 -> CoverInside(coverColor)
            day != null -> TextPage(model, day, topicTitles[day.date])
            else -> Paper(isRight = false)
        }
    }
}

@Composable
private fun CoverFront(
    model: RecordBookModel,
    images: Map<String, ImageBitmap>,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(COVER_WIDTH.dp, COVER_HEIGHT.dp)
            .background(color),
    ) {
        // 책등을 감싼 띠.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(SPINE_BAND_WIDTH.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Ink.copy(alpha = 0.25f), Color.White.copy(alpha = 0.08f), Ink.copy(alpha = 0.12f)),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = COVER_CONTENT_TOP.dp),
        ) {
            model.firstPhotoPost?.let { post ->
                RecordPhotoImage(
                    image = images[post.postId],
                    modifier = Modifier
                        .shadow(1.dp, RectangleShape)
                        .background(ChalkakTheme.colors.inputBackground)
                        .padding(TIP_PADDING.dp)
                        .size(TIP_PHOTO_SIZE.dp),
                )
            }
            Text(
                text = "${model.month.year}",
                color = ChalkakTheme.colors.textOnImage
                    .copy(alpha = 0.8f),
                style = ChalkakTheme.typography.caption
                    .copy(letterSpacing = COVER_YEAR_TRACKING.sp),
            )
            Text(
                text = "${model.month.monthValue}월",
                color = ChalkakTheme.colors.textOnImage,
                style = ChalkakTheme.typography.title2,
            )
        }
    }
}

/** 표지 안쪽 면지. 판 가장자리가 면지 바깥으로 조금 드러난다. */
@Composable
private fun CoverInside(color: Color) {
    Box(
        modifier = Modifier
            .size(COVER_WIDTH.dp, COVER_HEIGHT.dp)
            .background(color)
            .padding(start = COVER_MARGIN.dp, top = COVER_MARGIN.dp, bottom = COVER_MARGIN.dp)
            .background(lerp(color, Color.White, ENDPAPER_LIGHTEN)),
    )
}

/** 속표지. 찰캌 로고, "N월의 기록", 그 아래 내 사인. */
@Composable
private fun TitlePage(
    model: RecordBookModel,
    signatureUrl: String?,
) {
    Paper(isRight = true) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
            modifier = Modifier.align(Alignment.Center),
        ) {
            // 로고와 같은 글씨를 줄여 머리글처럼 쓴다.
            ChalkakLogo(
                modifier = Modifier
                    .height(TITLE_LOGO_HEIGHT.dp)
                    .graphicsLayer {
                        scaleX = TITLE_LOGO_SCALE
                        scaleY = TITLE_LOGO_SCALE
                    }.clearAndSetSemantics { },
            )
            Text(
                text = "${model.month.monthValue}월의 기록",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
            )
            if (signatureUrl != null) {
                // 사인은 사진 위에 얹도록 흰 선으로 저장된다. 밝은 종이 위에서는 글자색으로 물들여 보이게 한다.
                ChalkakImage(
                    model = signatureUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(ChalkakTheme.colors.textPrimary, BlendMode.Modulate),
                    modifier = Modifier
                        .padding(top = ChalkakTheme.spacing.xs)
                        .size(SIGNATURE_WIDTH.dp, SIGNATURE_HEIGHT.dp),
                )
            }
        }
    }
}

/** 펼친 책의 오른쪽 면. 사진을 원래 비율 그대로 면 가운데에 붙인다. */
@Composable
private fun PhotoPage(
    day: RecordPhotoStackEntry,
    images: Map<String, ImageBitmap>,
    hiddenPostId: () -> String?,
) {
    Paper(isRight = true) {
        val post = day.post
        if (post != null) {
            val image = images[post.postId]
            val frame = RecordBookGeometry.photoFrame(RecordPhotoAspect.widthRatio(image))
            RecordPhotoImage(
                image = image,
                modifier = Modifier
                    .offset(x = (frame.left - SPINE_X).dp, y = (frame.top - PAGE_TOP).dp)
                    .size(frame.width.dp, frame.height.dp)
                    // 피드로 오가는 동안에는 사진을 그리지 않는다. 이 면만 다시 녹화된다.
                    .drawWithContent { if (hiddenPostId() != post.postId) drawContent() }
                    .border(PHOTO_EDGE_WIDTH.dp, Ink.copy(alpha = PHOTO_EDGE_ALPHA)),
            )
        } else {
            // 기록이 없는 날 사진 자리에 적는 말.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Rule()
                Text(
                    text = "이날은 사진이 없어요",
                    color = ChalkakTheme.colors.textInactive,
                    style = ChalkakTheme.typography.caption,
                )
            }
        }
    }
}

/** 펼친 책의 왼쪽 면. 요일, 날짜, 그날의 주제. */
@Composable
private fun TextPage(
    model: RecordBookModel,
    day: RecordPhotoStackEntry,
    topic: String?,
) {
    Paper(isRight = false) {
        Text(
            text = "${model.month.monthValue}월의 기록",
            color = ChalkakTheme.colors.textMuted,
            style = ChalkakTheme.typography.caption,
            maxLines = 1,
            modifier = Modifier.padding(start = HEADER_INSET.dp, top = HEADER_TOP.dp, end = HEADER_INSET.dp),
        )
        Column(
            modifier = Modifier.padding(start = TEXT_INSET.dp, top = TEXT_TOP.dp, end = (TEXT_INSET + 4).dp),
        ) {
            Text(
                text = day.date.format(RecordStackFormat.weekday),
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.caption,
            )
            Text(
                text = "${day.day}",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.title1
                    .copy(fontFeatureSettings = TABULAR_NUMBERS),
            )
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
            Rule()
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
            if (topic != null) {
                Text(
                    text = topic,
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.footnote,
                    maxLines = TOPIC_LINE_LIMIT,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Rule() {
    Box(
        modifier = Modifier
            .size(RULE_WIDTH.dp, RULE_HEIGHT.dp)
            .background(ChalkakTheme.colors.calendarCellBorder),
    )
}

/** 종이. 책등 쪽으로 갈수록 골로 빛이 빠져 어두워지고, 바깥쪽은 조금 밝다. */
@Composable
private fun Paper(
    isRight: Boolean,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val stops = arrayOf(
        0f to Ink.copy(alpha = GUTTER_DARK),
        0.07f to Ink.copy(alpha = GUTTER_DARK * 0.35f),
        0.2f to Ink.copy(alpha = 0f),
        0.82f to Ink.copy(alpha = 0f),
        1f to Ink.copy(alpha = GUTTER_DARK * 0.3f),
    )
    val gutter = if (isRight) {
        Brush.horizontalGradient(*stops)
    } else {
        Brush.horizontalGradient(
            *stops
                .map { (location, color) -> (1f - location) to color }
                .reversed()
                .toTypedArray(),
        )
    }
    Box(
        modifier = Modifier
            .size(PAGE_WIDTH.dp, PAGE_HEIGHT.dp)
            .background(ChalkakTheme.colors.inputBackground)
            .background(gutter),
        content = content,
    )
}

/** 책 한 권의 장 구성. 화면 크기와 무관한 값만 다룬다. */
class RecordBookModel(
    val month: YearMonth,
    posts: List<PostCalendarItem>,
    today: LocalDate,
) {
    /** 1일부터 날짜순. */
    val days: List<RecordPhotoStackEntry> = RecordPhotoStackOrder(
        month = month,
        posts = posts,
        elapsedDays = month.elapsedDayCount(today),
    ).entries.reversed()

    /** 마지막 날이 펼쳐지는 펼침면. 표지(0), 속표지(1) 다음에 날마다 한 장이다. */
    val maxSpread: Int
        get() = days.size + 1

    val firstPhotoPost: PostCalendarItem?
        get() = days.firstOrNull { it.post != null }?.post

    /** 묶음 위치(최근 날이 0)를 넘긴 장 수로 바꾼다. 끝을 넘겨 끄는 동안에는 범위를 넘을 수 있다. */
    fun spread(position: Float): Float = maxSpread - position

    /** 그 펼침면에서 오른쪽에 펼쳐진 날. 표지와 속표지는 null. */
    fun day(spread: Int): RecordPhotoStackEntry? = days.getOrNull(spread - 2)

    fun post(spread: Int): PostCalendarItem? = day(spread)?.post

    fun accessibilityLabel(spread: Int): String {
        if (spread <= 0) return "${month.year}년 ${month.monthValue}월 책 표지"
        val day = day(spread) ?: return "${month.monthValue}월의 기록 속표지"
        val date = day.date.format(RecordStackFormat.monthDay)
        return if (day.post == null) "$date, 기록 없음" else "$date 사진"
    }
}

/** 사진 자리(책 무대 dp 좌표). */
data class RecordBookFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** 책을 그리는 자리. 앱 묶음 자리(393×290)를 기준으로 하고 화면 폭에 맞춰 줄인다. */
object RecordBookGeometry {
    /**
     * 묶음이 커질 때 책이 커질 수 있는 최대 배율. 펼친 책이 화면 양옆 [GROWTH_GUTTER] 안쪽에 머문다.
     *
     * @param width 책 무대 기준 화면 폭(무대를 화면 폭에 맞춘 뒤의 폭).
     */
    fun maxGrowth(width: Float): Float = maxOf(1f, (width / 2 - GROWTH_GUTTER) / COVER_WIDTH)

    /** 사진 자리. 오른쪽 면 안쪽 가운데에 원래 비율 그대로 맞춘다. 예) 3:4는 132×176, 4:3은 132×99. */
    fun photoFrame(ratio: Float): RecordBookFrame {
        val areaWidth = PAGE_WIDTH - SPREAD_PHOTO_INSET * 2
        val areaHeight = PAGE_HEIGHT - SPREAD_PHOTO_INSET * 2
        val (width, height) = if (areaWidth / ratio <= areaHeight) {
            areaWidth to areaWidth / ratio
        } else {
            areaHeight * ratio to areaHeight
        }
        val centerX = SPINE_X + PAGE_WIDTH / 2
        val centerY = PAGE_TOP + PAGE_HEIGHT / 2
        return RecordBookFrame(centerX - width / 2, centerY - height / 2, width, height)
    }

    /** 펼친 책 왼쪽 화살표의 가운데 x. 책 가장자리와 화면 끝 사이 가운데에 두되 화면 밖으로 나가지 않게 한다. */
    fun arrowCenterX(): Float = maxOf(ARROW_TOUCH / 2, (SPINE_X - COVER_WIDTH) / 2)
}

/**
 * 넘어가는 종이의 휜 모양. 손으로 종이 끝을 잡아 넘기듯, 책등에서 종이 끝으로 갈수록 넘기는 쪽으로 더 넘어가 휜다.
 * - 기울기는 책등 쪽이 turn만큼(0이면 오른쪽에 누움, 1이면 왼쪽에 누움) 넘어가 있고, 끝으로 갈수록 거리의 제곱만큼 앞선다.
 * - 휘는 정도는 넘기는 한가운데에서 가장 크고, 다 넘어가면 다시 평평해진다.
 * - 띠마다 원근을 넣은 사영 변환을 만든다. 화면에 가까이 들린 쪽이 조금 커 보인다.
 */
object RecordPaperCurl {
    class Segment(
        /** 띠의 기울기(라디안). 0은 오른쪽에 누운 면, π는 왼쪽에 누운 면이다. */
        val angle: Float,
        /** 띠 양 끝(책등 쪽, 종이 끝 쪽)에서의 기울기. 명암을 띠 사이에 끊기지 않게 잇는 데 쓴다. */
        val startAngle: Float,
        val endAngle: Float,
        /** android.graphics.Matrix 순서(scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2)의 dp 기준 변환. */
        val matrixValues: FloatArray,
    ) {
        val showsFront: Boolean
            get() = angle < PI.toFloat() / 2
    }

    /** 넘기는 한가운데에서 종이 끝이 책등 쪽보다 앞서는 기울기(라디안, 약 55°). */
    const val MAX_BEND = 0.95f
    const val DEPTH_RATIO = 3.2f

    /**
     * @param turn 넘어간 정도(0~1).
     * @param direction 다음 장으로 넘기는 중이면 1, 되돌리는 중이면 -1. 그 사이 값이면 덜 휜다.
     */
    fun segments(
        turn: Float,
        direction: Float,
        width: Float,
        height: Float,
        count: Int,
    ): List<Segment> {
        val pi = PI.toFloat()
        val stripWidth = width / count
        val base = pi * turn
        val bend = MAX_BEND * sin(pi * turn) * direction
        // 원근의 깊이. 화면에서 이만큼 떨어진 눈으로 본다.
        val depth = width * DEPTH_RATIO
        val centerY = height / 2
        fun tilt(along: Float) = (base + bend * along * along).coerceIn(0f, pi)
        var x = 0f
        var z = 0f
        return (0 until count).map { strip ->
            val angle = tilt((strip + 0.5f) / count)
            val nextX = x + stripWidth * cos(angle)
            val nextZ = z + stripWidth * sin(angle)
            // 띠의 가로 위치 u에서 화면 좌표는 X = x(u) / w(u), Y = centerY + (y - centerY) / w(u)이고 w = 1 - z / depth다.
            val a = 1 - z / depth
            val b = -(nextZ - z) / (depth * stripWidth)
            val values = floatArrayOf(
                (nextX - x) / stripWidth, 0f, x,
                centerY * b, 1f, centerY * (a - 1),
                b, 0f, a,
            )
            Segment(
                angle = angle,
                startAngle = tilt(strip.toFloat() / count),
                endAngle = tilt((strip + 1f) / count),
                matrixValues = values,
            ).also {
                x = nextX
                z = nextZ
            }
        }
    }
}

/** 휘는 방향. 다음 장으로 넘기면 1, 앞 장으로 되돌리면 -1이다. 종이 끝이 이 방향으로 앞서며 휜다. */
private class RecordBookBend {
    val direction = Animatable(1f)
    var target = 1f

    /** 지금 방향으로 가장 멀리 간 위치(넘긴 장 수). 여기서 반대로 조금 이상 돌아와야 방향을 바꾼다. */
    var anchor: Float? = null
}

/** 천 장정 색. 사진 대표색을 어둡게 눌러 쓴다. */
private fun clothOf(base: Color): Color = lerp(base, ClothDarkInk, CLOTH_DARKEN)

/** 여러 장을 한꺼번에 넘길수록 조금 더 천천히 넘긴다. 강성은 (2π / 응답 시간)²이다. */
private fun turnSpring(distance: Float) = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = minOf(1.1f, 0.55f + 0.05f * distance).let { response ->
        val omega = 2 * PI.toFloat() / response
        omega * omega
    },
)

private const val STAGE_WIDTH = 393f
private const val STAGE_HEIGHT = 290f

/** 낱장 한 면. 양 끝에 화살표(44dp)가 들어갈 자리를 남긴다. */
private const val PAGE_WIDTH = 152f
private const val PAGE_HEIGHT = 198f

/** 낱장이 붙은 책등의 x. 화면 가운데다. */
private const val SPINE_X = STAGE_WIDTH / 2

/** 책 무대 안에서 책 면의 위쪽. 화면에서는 표지 위쪽이 CONTENT_TOP에 오도록 무대를 옮겨 그린다. */
private const val PAGE_TOP = 72f

/** 이만큼 끌면 한 장 넘어간다. */
private const val DRAG_STEP = 152f

/** 손을 뗀 속도로 이 시간만큼 더 끈 것으로 보고 넘길 장을 정한다. */
private const val FLING_PROJECTION_SECONDS = 0.2f
private const val MAX_FLING = 2f
private const val GROWTH_GUTTER = 8f

/** 표지는 속지보다 위·아래·바깥쪽으로 같은 폭만큼 크다. 그래야 표지 테두리 두께가 둘레 어디서나 같다. */
private const val COVER_MARGIN = 4f
private const val COVER_WIDTH = PAGE_WIDTH + COVER_MARGIN
private const val COVER_HEIGHT = PAGE_HEIGHT + COVER_MARGIN * 2

/** 오른쪽 면에서 사진 둘레 여백. */
private const val SPREAD_PHOTO_INSET = 10f

/** 책 양 끝 화살표. 책과 화면 끝 사이 좁은 자리에 들어가도록 작게 그린다. */
private const val ARROW_DIAMETER = 40f
private const val ARROW_ICON_SIZE = 20f
private const val ARROW_TOUCH = 48f

/** 책이 커졌을 때 화살표가 화면 끝에서 떨어지는 거리. */
private const val ARROW_EDGE_INSET = 4f
private const val SETTLED_TOLERANCE = 0.05f
private const val BEND_FLIP_MILLIS = 400

/** 방향을 바꾸려면 반대로 돌아와야 하는 거리(장 수). */
private const val BEND_FLIP_THRESHOLD = 0.04f
private const val OVERSCROLL_SHIFT = 28f
private const val SHADOW_RADIUS = 10f
private const val DESK_SHADOW_ELEVATION = 8f
private const val SPINE_WIDTH = 10f
private const val SPINE_RADIUS = 3f
private const val SPINE_DARKEN = 0.25f
private const val COVER_RADIUS = 5f
private const val COVER_INNER_RADIUS = 2f

/** 낱장이 넘어갈 때 원근. 카메라를 낱장 폭의 몇 배 떨어뜨릴지(dp 기준)다. */
private const val LEAF_CAMERA_DISTANCE = 12f
private const val TURN_SHADE_ALPHA = 0.16f

/** 넘기는 종이를 나누는 세로 띠 수. */
private const val PAPER_STRIPS = 10
private const val PAPER_STRIP_OVERLAP = 0.6f
private const val CAST_SHADOW_ALPHA = 0.28f
private const val LEAF_Z_INDEX = 100f
private const val TURNING_Z_INDEX = 400f
private const val SPINE_BAND_WIDTH = 7f
private const val TIP_PHOTO_SIZE = 76f
private const val TIP_PADDING = 4f
private const val COVER_CONTENT_TOP = 24f
private const val COVER_YEAR_TRACKING = 2.6f

/** 속표지 로고. 로고 글씨를 이만큼 줄인다. */
private const val TITLE_LOGO_SCALE = 0.6f
private const val TITLE_LOGO_HEIGHT = 24f
private const val SIGNATURE_WIDTH = 72f
private const val SIGNATURE_HEIGHT = 40f
private const val ENDPAPER_LIGHTEN = 0.78f
private const val PHOTO_EDGE_WIDTH = 0.5f
private const val PHOTO_EDGE_ALPHA = 0.08f
private const val RULE_WIDTH = 22f
private const val RULE_HEIGHT = 1f
private const val TOPIC_LINE_LIMIT = 3
private const val TEXT_INSET = 16f
private const val TEXT_TOP = 32f
private const val HEADER_INSET = 14f
private const val HEADER_TOP = 12f
private const val GUTTER_DARK = 0.18f
private const val CLOTH_DARKEN = 0.55f

/** 천 색을 누르는 짙은 먹색(30, 34, 30). 사진 대표색을 이 색 쪽으로 당겨 천처럼 어둡게 만든다. */
private val ClothDarkInk = Color(red = 30, green = 34, blue = 30)

@Preview(showBackground = true, widthDp = 393, heightDp = 300)
@Composable
private fun RecordPhotoBookPreview() {
    val month = YearMonth.of(2026, 8)
    val posts = listOf(2, 5, 9).map { day ->
        PostCalendarItem(
            postId = "preview-$day",
            topicDate = month.atDay(day),
            thumbnailImageUrl = "",
            status = PostStatus.APPROVED,
        )
    }
    ChalkakTheme {
        RecordPhotoBook(
            month = month,
            posts = posts,
            images = emptyMap(),
            topicTitles = mapOf(month.atDay(5) to "반짝이는 순간"),
            position = remember { Animatable(20f) },
            onSelectPost = {},
            today = month.atDay(20),
            modifier = Modifier
                .fillMaxWidth()
                .height(RecordStackMetrics.Height),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 300)
@Composable
private fun RecordPhotoBookCoverPreview() {
    val month = YearMonth.of(2026, 8)
    ChalkakTheme {
        RecordPhotoBook(
            month = month,
            posts = emptyList(),
            images = emptyMap(),
            topicTitles = emptyMap(),
            position = remember { Animatable(21f) },
            onSelectPost = {},
            today = month.atDay(20),
            modifier = Modifier
                .fillMaxWidth()
                .height(RecordStackMetrics.Height),
        )
    }
}
