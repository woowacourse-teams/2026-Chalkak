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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.component.HomePhotoList
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopic
import com.stonefive.chalkak.feature.home.component.homeBottomDivider
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
    val interactionScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val fixedTopAreaHeightPx = statusBarHeightPx + with(density) {
        HomeTopBarHeight.toPx()
    }
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
        topAreaOffset = 0f
        isTopAreaTargetHidden = false
        bottomBarOffset = 0f
    }

    fun settleBars() {
        settleJob.value?.cancel()

        val initialTopOffset = topAreaOffset
        val targetTopOffset = settleBarOffset(
            currentOffset = initialTopOffset,
            hiddenOffset = -topAreaHeight.toFloat(),
            restingOffset = if (isTopAreaTargetHidden) -topAreaHeight.toFloat() else 0f,
        )
        val initialBottomOffset = bottomBarOffset
        val targetBottomOffset = 0f
        isTopAreaTargetHidden = targetTopOffset != 0f

        if (initialTopOffset == targetTopOffset &&
            initialBottomOffset == targetBottomOffset
        ) {
            return
        }

        settleJob.value = interactionScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = BAR_SETTLE_DURATION_MILLIS),
            ) { progress, _ ->
                topAreaOffset = initialTopOffset +
                    ((targetTopOffset - initialTopOffset) * progress)
                bottomBarOffset = initialBottomOffset +
                    ((targetBottomOffset - initialBottomOffset) * progress)
            }
        }
    }

    val nestedScrollConnection = remember(topAreaHeight, bottomBarHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y != 0f) {
                        settleJob.value?.cancel()
                        bottomBarOffset = bottomBarOffsetAfterScroll(
                            currentOffset = bottomBarOffset,
                            scrollDelta = available.y,
                            barHeight = bottomBarHeight.toFloat(),
                        )
                    }

                    if (available.y < 0f && topAreaHeight > 0) {
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
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    topAreaOffset < 0f
                ) {
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
                settleBars()
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
private const val COLLAPSED_TOP_BAR_BACKGROUND_ALPHA = 0.86f
private const val TOP_BAR_FADE_START_PROGRESS = 0.8f
private const val BAR_SETTLE_DURATION_MILLIS = 220

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

internal fun settleBarOffset(
    currentOffset: Float,
    hiddenOffset: Float,
    restingOffset: Float,
): Float {
    if (hiddenOffset == 0f) return 0f

    val hiddenProgress = (currentOffset / hiddenOffset).coerceIn(0f, 1f)
    return when {
        hiddenProgress > 0.5f -> hiddenOffset
        hiddenProgress < 0.5f -> 0f
        else -> restingOffset
    }
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
