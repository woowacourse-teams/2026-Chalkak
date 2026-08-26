package com.stonefive.chalkak.feature.display

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.display.component.DisplayDateHeader
import com.stonefive.chalkak.feature.display.component.DisplaySortTabs
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DisplayRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    initialDate: LocalDate? = null,
    modifier: Modifier = Modifier,
    viewModel: DisplayViewModel = viewModel(
        key = "display-${initialDate ?: "latest"}",
        factory = DisplayViewModel.factory(initialDate),
    ),
    onOpenFeed: (Post, String, String) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisplayScreen(
        uiState = uiState,
        onPreviousDateClick = viewModel::moveToPreviousDate,
        onNextDateClick = viewModel::moveToNextDate,
        onSortSelected = viewModel::selectSort,
        onFeaturedPageChanged = viewModel::updateFeaturedPage,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onOpenFeed = onOpenFeed,
        modifier = modifier,
    )
}

@Composable
fun DisplayScreen(
    uiState: DisplayUiState,
    onPreviousDateClick: () -> Unit,
    onNextDateClick: () -> Unit,
    onSortSelected: (PostSort) -> Unit,
    onFeaturedPageChanged: (Int) -> Unit,
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    onOpenFeed: (Post, String, String) -> Unit = { _, _, _ -> },
) {
    val gridState = rememberLazyStaggeredGridState()
    var headerOffset by remember { mutableFloatStateOf(0f) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var isHeaderTargetHidden by remember { mutableStateOf(false) }
    var filterOffset by remember { mutableFloatStateOf(0f) }
    var filterHeight by remember { mutableIntStateOf(0) }
    var isFilterTargetHidden by remember { mutableStateOf(false) }
    var bottomBarOffset by remember { mutableFloatStateOf(0f) }
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    var isBottomBarTargetHidden by remember { mutableStateOf(false) }
    var isScrollToTopButtonVisible by remember { mutableStateOf(false) }
    var scrollToTopAccumulated by remember { mutableFloatStateOf(0f) }
    val settleScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val bottomBarRestoreJob = remember { mutableStateOf<Job?>(null) }
    val selectedSort = (uiState.content as? DisplayContentState.Latest)?.selectedSort
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val scrollToTopToggleThresholdPx = with(density) { ScrollToTopToggleThreshold.toPx() }
    val filterContributionPx = if (uiState.content is DisplayContentState.Latest) {
        filterHeight + filterOffset
    } else {
        0f
    }
    val visibleTopAreaHeightPx = (
        statusBarHeightPx +
            headerHeight + headerOffset +
            filterContributionPx
        ).coerceAtLeast(0f)
    val bodyTopContentPadding = with(density) { visibleTopAreaHeightPx.toDp() }

    fun settleTopAreas() {
        settleJob.value?.cancel()
        val initialHeaderOffset = headerOffset
        val targetHeaderOffset = settleDisplayAreaOffset(
            currentOffset = initialHeaderOffset,
            hiddenOffset = -headerHeight.toFloat(),
            restingOffset = if (isHeaderTargetHidden) -headerHeight.toFloat() else 0f,
        )
        val initialFilterOffset = filterOffset
        val targetFilterOffset = settleDisplayAreaOffset(
            currentOffset = initialFilterOffset,
            hiddenOffset = -filterHeight.toFloat(),
            restingOffset = if (isFilterTargetHidden) -filterHeight.toFloat() else 0f,
        )
        isHeaderTargetHidden = targetHeaderOffset != 0f
        isFilterTargetHidden = targetFilterOffset != 0f

        if (initialHeaderOffset == targetHeaderOffset &&
            initialFilterOffset == targetFilterOffset
        ) {
            return
        }

        settleJob.value = settleScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
            ) { progress, _ ->
                headerOffset = initialHeaderOffset +
                    ((targetHeaderOffset - initialHeaderOffset) * progress)
                filterOffset = initialFilterOffset +
                    ((targetFilterOffset - initialFilterOffset) * progress)
            }
        }
    }

    fun settleBottomBar() {
        bottomBarRestoreJob.value?.cancel()
        val initialOffset = bottomBarOffset
        val hiddenOffset = bottomBarHeight.toFloat()

        val targetOffset = settleDisplayAreaOffset(
            currentOffset = initialOffset,
            hiddenOffset = hiddenOffset,
            restingOffset = if (isBottomBarTargetHidden) hiddenOffset else 0f,
        )
        isBottomBarTargetHidden = targetOffset != 0f

        if (initialOffset == targetOffset) return

        bottomBarRestoreJob.value = settleScope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = BAR_SETTLE_DURATION_MILLIS),
            ) { value, _ -> bottomBarOffset = value }
        }
    }

    fun resetScrollState() {
        settleJob.value?.cancel()
        bottomBarRestoreJob.value?.cancel()
        headerOffset = 0f
        isHeaderTargetHidden = false
        filterOffset = 0f
        isFilterTargetHidden = false
        bottomBarOffset = 0f
        isBottomBarTargetHidden = false
        isScrollToTopButtonVisible = false
        scrollToTopAccumulated = 0f
    }

    val nestedScrollConnection = remember(
        headerHeight,
        filterHeight,
        bottomBarHeight,
        uiState.content is DisplayContentState.Latest,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    bottomBarRestoreJob.value?.cancel()
                    val atTop = !gridState.canScrollBackward &&
                        headerOffset == 0f &&
                        filterOffset == 0f
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

                if (available.y < 0f) {
                    settleJob.value?.cancel()
                    var remainingScroll = available.y
                    var consumedScroll = 0f

                    if (headerHeight > 0) {
                        val previousOffset = headerOffset
                        headerOffset = (headerOffset + remainingScroll)
                            .coerceAtLeast(-headerHeight.toFloat())
                        val consumedByHeader = headerOffset - previousOffset
                        consumedScroll += consumedByHeader
                        remainingScroll -= consumedByHeader
                    }

                    if (remainingScroll < 0f &&
                        uiState.content is DisplayContentState.Latest &&
                        filterHeight > 0
                    ) {
                        val previousOffset = filterOffset
                        filterOffset = (filterOffset + remainingScroll)
                            .coerceAtLeast(-filterHeight.toFloat())
                        consumedScroll += filterOffset - previousOffset
                    }

                    if (consumedScroll != 0f) {
                        return Offset(0f, consumedScroll)
                    }
                }

                if (available.y > 0f &&
                    uiState.content is DisplayContentState.Latest &&
                    filterHeight > 0 &&
                    filterOffset < 0f
                ) {
                    settleJob.value?.cancel()
                    val previousOffset = filterOffset
                    filterOffset = (filterOffset + available.y).coerceAtMost(0f)
                    if (filterOffset != previousOffset) {
                        return Offset(0f, filterOffset - previousOffset)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && headerHeight > 0 && headerOffset < 0f) {
                    settleJob.value?.cancel()
                    val previousOffset = headerOffset
                    headerOffset = (headerOffset + available.y).coerceAtMost(0f)
                    return Offset(0f, headerOffset - previousOffset)
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
                settleTopAreas()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(uiState.selectedDate, selectedSort) {
        resetScrollState()
        gridState.scrollToItem(0)
    }

    LaunchedEffect(gridState.canScrollBackward, headerOffset, filterOffset) {
        if (!gridState.canScrollBackward && headerOffset == 0f && filterOffset == 0f) {
            isScrollToTopButtonVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground)
            .nestedScroll(nestedScrollConnection),
    ) {
        DisplayBody(
            content = uiState.content,
            onFeaturedPageChanged = onFeaturedPageChanged,
            gridState = gridState,
            onPhotoClick = { photo ->
                onOpenFeed(
                    photo,
                    uiState.selectedDate
                        ?.toFeedDateLabel()
                        .orEmpty(),
                    uiState.topic,
                )
            },
            topContentPadding = bodyTopContentPadding,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        ChalkakBackground.copy(alpha = DISPLAY_FLOATING_BACKGROUND_ALPHA),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .collapsingArea(headerOffset),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChalkakBackground)
                        .onSizeChanged { headerHeight = it.height },
                ) {
                    DisplayDateHeader(
                        selectedDate = uiState.selectedDate,
                        topic = uiState.topic,
                        isArchiveDate = uiState.content is DisplayContentState.Archive,
                        canGoPrevious = uiState.canGoPrevious,
                        canGoNext = uiState.canGoNext,
                        onPreviousClick = onPreviousDateClick,
                        onNextClick = onNextDateClick,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (uiState.content is DisplayContentState.Archive) {
                        Spacer(modifier = Modifier.height(15.dp))
                    }
                }
            }

            if (uiState.content is DisplayContentState.Latest) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            ChalkakBackground.copy(alpha = DISPLAY_FLOATING_BACKGROUND_ALPHA),
                        ).clipToBounds()
                        .collapsingArea(filterOffset),
                ) {
                    DisplaySortTabs(
                        selectedSort = uiState.content.selectedSort,
                        onSortSelected = onSortSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { filterHeight = it.height },
                    )
                }
            }
        }
        if (isScrollToTopButtonVisible) {
            Surface(
                onClick = {
                    settleScope.launch {
                        resetScrollState()
                        gridState.animateScrollToItem(0)
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
            selectedItem = ChalkakBottomBarItem.DISPLAY,
            onItemSelected = onNavigateToBottomBar,
            onAddClick = onOpenPhotoUpload,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarHeight = it.height }
                .graphicsLayer { translationY = bottomBarOffset },
        )
    }
}

private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"
