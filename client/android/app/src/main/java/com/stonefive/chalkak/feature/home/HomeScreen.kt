package com.stonefive.chalkak.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.component.HomePhotoList
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopic
import com.stonefive.chalkak.feature.home.component.homeBottomDivider
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    selectionSignal: Int = 0,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                HomeUiEvent.OpenPhotoUpload -> onOpenPhotoUpload()
                is HomeUiEvent.NavigateToBottomBar -> onNavigateToBottomBar(event.item)
            }
        }
    }

    LaunchedEffect(selectionSignal) {
        if (selectionSignal > 0) {
            viewModel.onHomeSelected()
        }
    }

    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        resetSignal = selectionSignal,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0,
) {
    val photoListState = rememberLazyListState()
    var localResetSignal by remember { mutableIntStateOf(0) }
    var topAreaOffset by remember { mutableFloatStateOf(0f) }
    var topAreaHeight by remember { mutableIntStateOf(0) }
    var isTopAreaTargetHidden by remember { mutableStateOf(false) }
    var bottomBarOffset by remember { mutableFloatStateOf(0f) }
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    var isBottomBarTargetHidden by remember { mutableStateOf(false) }
    var isScrollToTopButtonVisible by remember { mutableStateOf(false) }
    var scrollToTopAccumulated by remember { mutableFloatStateOf(0f) }
    val interactionScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val bottomBarRestoreJob = remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val fixedTopAreaHeightPx = statusBarHeightPx + with(density) {
        HomeTopBarHeight.toPx()
    }
    val scrollToTopToggleThresholdPx = with(density) { ScrollToTopToggleThreshold.toPx() }
    val visibleTopAreaHeightPx =
        (fixedTopAreaHeightPx + topAreaHeight + topAreaOffset).coerceAtLeast(0f)
    val isTopAreaVisible = topAreaHeight == 0 || topAreaOffset > -topAreaHeight.toFloat()
    val photoListTopPadding = with(density) { visibleTopAreaHeightPx.toDp() }
    val collapsedTopAreaProgress = if (topAreaHeight == 0) {
        0f
    } else {
        (-topAreaOffset / topAreaHeight).coerceIn(0f, 1f)
    }
    val topBarBackgroundAlpha = topBarBackgroundAlpha(collapsedTopAreaProgress)

    fun resetHomePosition() {
        settleJob.value?.cancel()
        bottomBarRestoreJob.value?.cancel()
        topAreaOffset = 0f
        isTopAreaTargetHidden = false
        bottomBarOffset = 0f
        isBottomBarTargetHidden = false
        isScrollToTopButtonVisible = false
        scrollToTopAccumulated = 0f
    }

    fun settleTopArea() {
        settleJob.value?.cancel()

        val initialTopOffset = topAreaOffset
        val targetTopOffset = settleBarOffset(
            currentOffset = initialTopOffset,
            hiddenOffset = -topAreaHeight.toFloat(),
            restingOffset = if (isTopAreaTargetHidden) -topAreaHeight.toFloat() else 0f,
        )
        isTopAreaTargetHidden = targetTopOffset != 0f

        if (initialTopOffset == targetTopOffset) return

        settleJob.value = interactionScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = BAR_SETTLE_DURATION_MILLIS),
            ) { progress, _ ->
                topAreaOffset = initialTopOffset +
                    ((targetTopOffset - initialTopOffset) * progress)
            }
        }
    }

    fun settleBottomBar() {
        bottomBarRestoreJob.value?.cancel()
        val initialOffset = bottomBarOffset
        val hiddenOffset = bottomBarHeight.toFloat()

        // 멈추면 가까운 쪽으로 정착하고 그대로 둔다. (절반 이상 내려갔으면 숨긴 채 유지)
        val targetOffset = settleBarOffset(
            currentOffset = initialOffset,
            hiddenOffset = hiddenOffset,
            restingOffset = if (isBottomBarTargetHidden) hiddenOffset else 0f,
        )
        isBottomBarTargetHidden = targetOffset != 0f

        if (initialOffset == targetOffset) return

        bottomBarRestoreJob.value = interactionScope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = BAR_SETTLE_DURATION_MILLIS),
            ) { value, _ -> bottomBarOffset = value }
        }
    }

    val nestedScrollConnection = remember(topAreaHeight, bottomBarHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    bottomBarRestoreJob.value?.cancel()
                    // 최상단(더 위로 스크롤할 게 없음)에서는 오버스크롤로도 버튼이
                    // 다시 켜지지 않게 강제로 끄고 누적을 리셋한다.
                    val atTop = !photoListState.canScrollBackward && topAreaOffset == 0f
                    if (atTop) {
                        scrollToTopAccumulated = 0f
                        isScrollToTopButtonVisible = false
                    } else {
                        val nextButtonState = scrollToTopButtonStateAfterScroll(
                            state = ScrollToTopButtonState(
                                accumulated = scrollToTopAccumulated,
                                visible = isScrollToTopButtonVisible,
                            ),
                            scrollDelta = available.y,
                            threshold = scrollToTopToggleThresholdPx,
                        )
                        scrollToTopAccumulated = nextButtonState.accumulated
                        isScrollToTopButtonVisible = nextButtonState.visible
                    }
                    bottomBarOffset = bottomBarOffsetAfterScroll(
                        currentOffset = bottomBarOffset,
                        scrollDelta = available.y,
                        barHeight = bottomBarHeight.toFloat(),
                    )
                }

                // 아래로 스크롤하면 드래그·플링 모두 리스트보다 먼저 상단 영역을 접는다.
                if (available.y < 0f &&
                    topAreaHeight > 0 &&
                    topAreaOffset > -topAreaHeight.toFloat()
                ) {
                    settleJob.value?.cancel()
                    val previousOffset = topAreaOffset
                    topAreaOffset = topAreaOffsetAfterScroll(
                        currentOffset = topAreaOffset,
                        scrollDelta = available.y,
                        areaHeight = topAreaHeight.toFloat(),
                    )
                    if (topAreaOffset != previousOffset) {
                        return Offset(0f, topAreaOffset - previousOffset)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && topAreaOffset < 0f) {
                    settleJob.value?.cancel()
                    val previousOffset = topAreaOffset
                    topAreaOffset = topAreaOffsetAfterScroll(
                        currentOffset = topAreaOffset,
                        scrollDelta = available.y,
                        areaHeight = topAreaHeight.toFloat(),
                    )
                    return Offset(0f, topAreaOffset - previousOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // 하단 바는 플링 중 움직이지 않으므로 여기서 바로 정착시킨다.
                settleBottomBar()
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // 상단 영역은 플링(관성)까지 접힘·펼침이 이어지므로,
                // 제스처가 완전히 끝난 뒤 한 번만 정착시켜 기준점이 도중에 뒤집히지 않게 한다.
                settleTopArea()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(uiState.selectedSort) {
        resetHomePosition()
        photoListState.scrollToItem(0)
    }

    LaunchedEffect(resetSignal, localResetSignal) {
        if (resetSignal == 0 && localResetSignal == 0) return@LaunchedEffect

        resetHomePosition()
        photoListState.animateScrollToItem(0)
    }

    LaunchedEffect(photoListState.canScrollBackward, topAreaOffset) {
        if (!photoListState.canScrollBackward && topAreaOffset == 0f) {
            isScrollToTopButtonVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground)
            .nestedScroll(nestedScrollConnection),
    ) {
        HomePhotoList(
            photos = uiState.photos,
            likedPhotoIds = uiState.likedPhotoIds,
            onLikeClick = { onAction(HomeUiAction.LikeClicked(it)) },
            modifier = Modifier.fillMaxSize(),
            state = photoListState,
            topContentPadding = photoListTopPadding,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(HomeTopBarHeight))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .collapsingTopArea(topAreaOffset),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChalkakBackground)
                        .onSizeChanged { topAreaHeight = it.height },
                ) {
                    HomeTopic(
                        dateLabel = uiState.dateLabel,
                        topic = uiState.topic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(ChalkakBackground.copy(alpha = topBarBackgroundAlpha)),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { localResetSignal++ },
                    ),
            )
            HomeTopBar(
                modifier = Modifier
                    .then(
                        if (isTopAreaVisible) {
                            Modifier.homeBottomDivider()
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (isScrollToTopButtonVisible) {
            Surface(
                onClick = {
                    interactionScope.launch {
                        resetHomePosition()
                        photoListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = with(density) { bottomBarHeight.toDp() } + 18.dp,
                    ).size(44.dp),
                shape = CircleShape,
                color = ChalkakWhite,
                shadowElevation = 12.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = "맨 위로",
                        tint = ChalkakTheme.colors.iconPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        ChalkakBottomBar(
            selectedItem = ChalkakBottomBarItem.TODAY,
            onItemSelected = { item ->
                if (item == ChalkakBottomBarItem.TODAY) {
                    localResetSignal++
                }
                onAction(HomeUiAction.BottomBarSelected(item))
            },
            onAddClick = { onAction(HomeUiAction.AddClicked) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeight = it.height }
                .graphicsLayer { translationY = bottomBarOffset },
        )
    }
}

private val HomeTopBarHeight = 55.dp
private val ScrollToTopToggleThreshold = 12.dp
private const val COLLAPSED_TOP_BAR_BACKGROUND_ALPHA = 0.86f
private const val TOP_BAR_FADE_START_PROGRESS = 0.8f
private const val BAR_SETTLE_DURATION_MILLIS = 220
private const val BAR_SETTLE_BREAK_THRESHOLD = 0.05f

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

internal fun bottomBarOffsetAfterScroll(
    currentOffset: Float,
    scrollDelta: Float,
    barHeight: Float,
): Float = (currentOffset - scrollDelta).coerceIn(0f, barHeight)

internal data class ScrollToTopButtonState(
    val accumulated: Float,
    val visible: Boolean,
)

/**
 * 매 프레임의 순간 방향 대신, 같은 방향으로 누적된 스크롤 거리가 임계값을 넘을 때만
 * 노출/숨김을 토글한다. 미세한 손가락 떨림이나 프레임 단위 부호 흔들림으로 인한
 * 깜빡임을 막기 위한 히스테리시스.
 */
internal fun scrollToTopButtonStateAfterScroll(
    state: ScrollToTopButtonState,
    scrollDelta: Float,
    threshold: Float,
): ScrollToTopButtonState {
    // 방향이 바뀌면 누적값을 리셋한다.
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

internal fun settleBarOffset(
    currentOffset: Float,
    hiddenOffset: Float,
    restingOffset: Float,
): Float {
    if (hiddenOffset == 0f) return 0f

    val restingProgress = restingOffset / hiddenOffset
    val currentProgress = (currentOffset / hiddenOffset).coerceIn(0f, 1f)
    val movedFromResting = abs(currentProgress - restingProgress)
    if (movedFromResting <= BAR_SETTLE_BREAK_THRESHOLD) return restingOffset
    return if (restingProgress == 0f) hiddenOffset else 0f
}

private fun Modifier.collapsingTopArea(offset: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val offsetPx = offset.toInt().coerceIn(-placeable.height, 0)
    val visibleHeight = (placeable.height + offsetPx).coerceAtLeast(0)

    layout(placeable.width, visibleHeight) {
        placeable.placeRelative(0, offsetPx)
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun HomeScreenPreview() {
    ChalkakTheme {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                dateLabel = "8월 3일 · 오늘의 주제",
                topic = "하늘하늘하늘",
                photos = listOf(
                    Post(
                        id = "preview-1",
                        imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "노을이 진 하늘과 전신주",
                        title = "안녕하세요 찰캌입니다.",
                        likeCount = 24,
                    ),
                    Post(
                        id = "preview-2",
                        imageUrl = drawableResourceUrl(R.drawable.preview_photo),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "두 번째 사진",
                        title = null,
                        likeCount = 12,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
