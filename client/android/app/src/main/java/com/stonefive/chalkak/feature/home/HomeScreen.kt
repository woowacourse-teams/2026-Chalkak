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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
                settleBottomBar()
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
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
