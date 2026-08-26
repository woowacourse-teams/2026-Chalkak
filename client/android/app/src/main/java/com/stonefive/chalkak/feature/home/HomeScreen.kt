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
import com.stonefive.chalkak.core.designsystem.scroll.COLLAPSING_SETTLE_DURATION_MILLIS
import com.stonefive.chalkak.core.designsystem.scroll.ChalkakScrollToTopButton
import com.stonefive.chalkak.core.designsystem.scroll.CollapsingScrollToTopThreshold
import com.stonefive.chalkak.core.designsystem.scroll.collapsingArea
import com.stonefive.chalkak.core.designsystem.scroll.rememberBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.settleCollapsingOffset
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
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
    val bottomBarState = rememberBottomBarScrollState()
    val interactionScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val fixedTopAreaHeightPx = statusBarHeightPx + with(density) {
        HomeTopBarHeight.toPx()
    }
    val scrollToTopToggleThresholdPx = with(density) { CollapsingScrollToTopThreshold.toPx() }
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
        bottomBarState.reset()
        topAreaOffset = 0f
        isTopAreaTargetHidden = false
    }

    fun settleTopArea() {
        settleJob.value?.cancel()

        val initialTopOffset = topAreaOffset
        val targetTopOffset = settleCollapsingOffset(
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
                animationSpec = tween(durationMillis = COLLAPSING_SETTLE_DURATION_MILLIS),
            ) { progress, _ ->
                topAreaOffset = initialTopOffset +
                    ((targetTopOffset - initialTopOffset) * progress)
            }
        }
    }

    val nestedScrollConnection = remember(topAreaHeight, bottomBarState.height) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val atTop = !photoListState.canScrollBackward && topAreaOffset == 0f
                    bottomBarState.onScroll(
                        scrollDeltaY = available.y,
                        atTop = atTop,
                        toggleThresholdPx = scrollToTopToggleThresholdPx,
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
                bottomBarState.settle(interactionScope)
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
            bottomBarState.hideScrollToTopButton()
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
                    .collapsingArea(topAreaOffset),
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
        if (bottomBarState.isScrollToTopButtonVisible) {
            ChalkakScrollToTopButton(
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
                        bottom = with(density) { bottomBarState.height.toDp() } + 18.dp,
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
                .onSizeChanged { bottomBarState.height = it.height }
                .graphicsLayer { translationY = bottomBarState.offset },
        )
    }
}

private val HomeTopBarHeight = 55.dp
