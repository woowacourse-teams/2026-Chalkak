package com.stonefive.chalkak.feature.filter.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.filter.FilterItem
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 필터 상세 시트. 작게(화면 절반)와 크게(전체) 두 단계로만 움직이고, 아래로 끌면 닫힌다.
 * 작게 열린 동안에는 대표 사진·이름·설명만 보이고, 키울수록 나머지 사진이 나타난다.
 * 사용 버튼은 시트 밖에 있어 시트가 움직여도 화면 아래에 그대로 있다.
 */
@Composable
fun FilterDetailSheet(
    filter: FilterItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onUseClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeightPx = constraints.maxHeight.toFloat()
        val sheetHeightPx = fullHeightPx - WindowInsets.statusBars.getTop(density)
        val navigationBarHeight = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        // 작게 열린 높이는 대표 사진·이름·설명과 사용 버튼에 맞춘다. 내용이 짧든 길든 너무 낮거나 높지 않게 제한한다.
        var summaryHeightPx by remember { mutableIntStateOf(0) }
        val collapsedVisiblePx = with(density) {
            (
                HandleVerticalPadding * 2 + HandleHeight + ChalkakTheme.spacing.xl +
                    UseButtonHeight + UseButtonBottomMargin + navigationBarHeight
                ).toPx() + summaryHeightPx
        }.coerceIn(fullHeightPx * COLLAPSED_MIN_FRACTION, fullHeightPx * COLLAPSED_MAX_FRACTION)
        val collapsedOffsetPx = sheetHeightPx - collapsedVisiblePx
        // 0이면 크게, collapsedOffsetPx면 작게, sheetHeightPx면 화면 밖이다. 처음에는 화면 밖에서 올라온다.
        var offsetPx by remember(sheetHeightPx) { mutableFloatStateOf(sheetHeightPx) }
        val scope = rememberCoroutineScope()

        suspend fun settleTo(target: Float) {
            animate(initialValue = offsetPx, targetValue = target) { value, _ -> offsetPx = value }
            if (target >= sheetHeightPx) onDismiss()
        }

        fun targetFor(velocityY: Float): Float {
            val anchors = listOf(0f, collapsedOffsetPx, sheetHeightPx)
            return when {
                velocityY > FLING_VELOCITY -> anchors.firstOrNull { it > offsetPx + 1f } ?: sheetHeightPx
                velocityY < -FLING_VELOCITY -> anchors.lastOrNull { it < offsetPx - 1f } ?: 0f
                else -> anchors.minBy { abs(it - offsetPx) }
            }
        }

        // 내용 크기를 잰 뒤에 올라와야 열리는 도중에 높이가 바뀌지 않는다.
        LaunchedEffect(collapsedOffsetPx, summaryHeightPx > 0) {
            if (summaryHeightPx > 0) settleTo(collapsedOffsetPx)
        }
        BackHandler { scope.launch { settleTo(sheetHeightPx) } }

        val connection = remember(sheetHeightPx, collapsedOffsetPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // 시트가 아직 다 안 올라왔으면 위로 끄는 동작은 내용 스크롤보다 시트가 먼저 쓴다.
                    if (available.y >= 0f || offsetPx <= 0f) return Offset.Zero
                    val next = (offsetPx + available.y).coerceAtLeast(0f)
                    val consumed = next - offsetPx
                    offsetPx = next
                    return Offset(0f, consumed)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // 내용이 맨 위일 때 아래로 끄는 동작은 시트를 내린다.
                    if (available.y <= 0f) return Offset.Zero
                    val next = (offsetPx + available.y).coerceAtMost(sheetHeightPx)
                    val used = next - offsetPx
                    offsetPx = next
                    return Offset(0f, used)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offsetPx <= 0f) return Velocity.Zero
                    settleTo(targetFor(available.y))
                    return available
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (offsetPx > 0f) settleTo(targetFor(available.y))
                    return available
                }
            }
        }

        // 보일수록 1, 닫혀 갈수록 0.
        val visibility = (1f - (offsetPx - collapsedOffsetPx).coerceAtLeast(0f) / (sheetHeightPx - collapsedOffsetPx))
            .coerceIn(0f, 1f)
        // 작게 열렸을 때 0, 크게 열렸을 때 1.
        val expandProgress = (1f - offsetPx / collapsedOffsetPx).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = visibility }
                .background(ChalkakTheme.colors.scrim)
                .clickable(interactionSource = null, indication = null) {
                    scope.launch { settleTo(sheetHeightPx) }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, (fullHeightPx - sheetHeightPx + offsetPx).roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { sheetHeightPx.toDp() })
                .clip(
                    ChalkakTheme.shapes.sheet
                        .copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
                ).background(ChalkakTheme.colors.surfaceElevated)
                .nestedScroll(connection)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Column(modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.lg)) {
                FilterSummary(
                    filter = filter,
                    isFavorite = isFavorite,
                    onFavoriteToggle = onFavoriteToggle,
                    modifier = Modifier.onSizeChanged { summaryHeightPx = it.height },
                )
                Column(modifier = Modifier.graphicsLayer { alpha = expandProgress }) {
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                    FilterPhotoSections(filter = filter)
                    // 아래에 고정된 사용 버튼이 마지막 사진을 가리지 않도록 비운다.
                    Spacer(
                        modifier = Modifier.height(
                            UseButtonHeight + UseButtonBottomMargin + navigationBarHeight + ChalkakTheme.spacing.xxl,
                        ),
                    )
                }
            }
        }

        ChalkakButton(
            text = "이 필터 사용해보기",
            onClick = onUseClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = visibility }
                .navigationBarsPadding()
                .padding(horizontal = ChalkakTheme.spacing.lg)
                .padding(bottom = UseButtonBottomMargin)
                .fillMaxWidth()
                .height(UseButtonHeight),
        )
    }
}

/** 누를 수 없는 표시용 손잡이. 끌어서 움직이는 건 시트 전체가 받는다. */
@Composable
private fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = HandleVerticalPadding)
            .size(HandleWidth, HandleHeight)
            .clip(CircleShape)
            .background(ChalkakTheme.colors.iconSecondary),
    )
}

@Composable
private fun FilterSummary(
    filter: FilterItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box {
            ChalkakImage(
                model = filter.coverImageRes,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(ChalkakTheme.shapes.photoCard),
            )
            FilterFavoriteButton(
                isFavorite = isFavorite,
                onToggle = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ChalkakTheme.spacing.sm),
            )
            Text(
                text = filter.creatorSignature,
                color = ChalkakTheme.colors.textOnImage,
                style = ChalkakTheme.typography.headline,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ChalkakTheme.spacing.sm),
            )
        }
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        Text(
            text = filter.name,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        Text(
            text = filter.description,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.caption,
        )
    }
}

@Composable
private fun FilterPhotoSections(
    filter: FilterItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "필터 제작자가 찍은 사진",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm)) {
            filter.creatorPhotoResList.forEach { photoRes ->
                ChalkakImage(
                    model = photoRes,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CREATOR_PHOTO_ASPECT_RATIO)
                        .clip(ChalkakTheme.shapes.photoCard),
                )
            }
        }
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
        Text(
            text = "이 필터로 찍은 사진",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm)) {
            filter.usedPhotoResList.forEach { photoRes ->
                ChalkakImage(
                    model = photoRes,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(ChalkakTheme.shapes.photoCard),
                )
            }
        }
    }
}

private const val COVER_ASPECT_RATIO = 2.2f
private const val COLLAPSED_MIN_FRACTION = 0.3f
private const val COLLAPSED_MAX_FRACTION = 0.85f
private const val FLING_VELOCITY = 1200f
private val HandleVerticalPadding = 12.dp
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private const val CREATOR_PHOTO_ASPECT_RATIO = 2.2f
private val UseButtonHeight = 56.dp
private val UseButtonBottomMargin = 16.dp

private val PreviewFilter = FilterItem(
    id = "preview",
    name = "0000필터",
    description = "언제 언제 쓰면 예뻐요",
    creatorSignature = "제작자\n사인",
    coverImageRes = R.drawable.preview_photo,
    creatorPhotoResList = List(2) { R.drawable.preview_photo },
    usedPhotoResList = List(3) { R.drawable.preview_photo },
)

@Composable
private fun FilterDetailSheetPreviewContent(isExpanded: Boolean) {
    ChalkakTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.surfaceElevated),
        ) {
            SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Column(modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.lg)) {
                FilterSummary(filter = PreviewFilter, isFavorite = isExpanded, onFavoriteToggle = {})
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                    FilterPhotoSections(filter = PreviewFilter)
                }
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
                ChalkakButton(
                    text = "이 필터 사용해보기",
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(UseButtonHeight),
                )
                Spacer(modifier = Modifier.height(UseButtonBottomMargin))
            }
        }
    }
}

@Preview(name = "필터 시트 작게", showBackground = true, widthDp = 390)
@Composable
private fun FilterDetailSheetCollapsedPreview() {
    FilterDetailSheetPreviewContent(isExpanded = false)
}

@Preview(name = "필터 시트 크게 즐겨찾기", showBackground = true, widthDp = 390)
@Composable
private fun FilterDetailSheetExpandedPreview() {
    FilterDetailSheetPreviewContent(isExpanded = true)
}
