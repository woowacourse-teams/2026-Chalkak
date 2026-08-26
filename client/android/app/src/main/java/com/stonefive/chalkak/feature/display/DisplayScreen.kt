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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.scroll.COLLAPSING_FLOATING_BACKGROUND_ALPHA
import com.stonefive.chalkak.core.designsystem.scroll.COLLAPSING_SETTLE_DURATION_MILLIS
import com.stonefive.chalkak.core.designsystem.scroll.ChalkakScrollToTopButton
import com.stonefive.chalkak.core.designsystem.scroll.CollapsingScrollToTopThreshold
import com.stonefive.chalkak.core.designsystem.scroll.collapsingArea
import com.stonefive.chalkak.core.designsystem.scroll.rememberBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.settleCollapsingOffset
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.display.component.DisplayDateHeader
import com.stonefive.chalkak.feature.display.component.DisplaySortTabs
import com.stonefive.chalkak.feature.display.component.previewDisplayPhotos
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
    val bottomBarState = rememberBottomBarScrollState()
    val settleScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val selectedSort = (uiState.content as? DisplayContentState.Latest)?.selectedSort
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val scrollToTopToggleThresholdPx = with(density) { CollapsingScrollToTopThreshold.toPx() }
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
        val targetHeaderOffset = settleCollapsingOffset(
            currentOffset = initialHeaderOffset,
            hiddenOffset = -headerHeight.toFloat(),
            restingOffset = if (isHeaderTargetHidden) -headerHeight.toFloat() else 0f,
        )
        val initialFilterOffset = filterOffset
        val targetFilterOffset = settleCollapsingOffset(
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

    fun resetScrollState() {
        settleJob.value?.cancel()
        bottomBarState.reset()
        headerOffset = 0f
        isHeaderTargetHidden = false
        filterOffset = 0f
        isFilterTargetHidden = false
    }

    val nestedScrollConnection = remember(
        headerHeight,
        filterHeight,
        bottomBarState.height,
        uiState.content is DisplayContentState.Latest,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val atTop = !gridState.canScrollBackward &&
                        headerOffset == 0f &&
                        filterOffset == 0f
                    bottomBarState.onScroll(
                        scrollDeltaY = available.y,
                        atTop = atTop,
                        toggleThresholdPx = scrollToTopToggleThresholdPx,
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
                bottomBarState.settle(settleScope)
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
            bottomBarState.hideScrollToTopButton()
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
                        ChalkakBackground.copy(alpha = COLLAPSING_FLOATING_BACKGROUND_ALPHA),
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
                            ChalkakBackground.copy(alpha = COLLAPSING_FLOATING_BACKGROUND_ALPHA),
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
        if (bottomBarState.isScrollToTopButtonVisible) {
            ChalkakScrollToTopButton(
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
                        bottom = with(density) { bottomBarState.height.toDp() } + 18.dp,
                    ),
            )
        }
        ChalkakBottomBar(
            selectedItem = ChalkakBottomBarItem.DISPLAY,
            onItemSelected = onNavigateToBottomBar,
            onAddClick = onOpenPhotoUpload,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBarState.height = it.height }
                .graphicsLayer { translationY = bottomBarState.offset },
        )
    }
}

private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"

private val previewLatestState = DisplayUiState(
    selectedDate = LocalDate.of(2026, 8, 5),
    latestDate = LocalDate.of(2026, 8, 5),
    earliestDate = LocalDate.of(2026, 8, 1),
    topic = "바다",
    content = DisplayContentState.Latest(
        photos = previewDisplayPhotos,
        selectedSort = PostSort.LATEST,
    ),
)

private val previewArchiveState = DisplayUiState(
    selectedDate = LocalDate.of(2026, 8, 4),
    latestDate = LocalDate.of(2026, 8, 5),
    earliestDate = LocalDate.of(2026, 8, 1),
    topic = "다리",
    content = DisplayContentState.Archive(
        photos = previewDisplayPhotos,
        featuredPhotos = previewDisplayPhotos,
    ),
)

@Preview(name = "최신 전시", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LatestDisplayScreenPreview() {
    DisplayScreenPreviewContent(uiState = previewLatestState)
}

@Preview(name = "과거 전시", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ArchiveDisplayScreenPreview() {
    DisplayScreenPreviewContent(uiState = previewArchiveState)
}

@Preview(name = "본문 로딩", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun DisplayLoadingContentPreview() {
    ChalkakTheme {
        DisplayBody(
            content = DisplayContentState.Loading,
            onFeaturedPageChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "최신 본문", showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun LatestDisplayContentPreview() {
    ChalkakTheme {
        LatestDisplayContent(
            content = previewLatestState.content as DisplayContentState.Latest,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "과거 본문", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun ArchiveDisplayContentPreview() {
    ChalkakTheme {
        ArchiveDisplayContent(
            content = previewArchiveState.content as DisplayContentState.Archive,
            onFeaturedPageChanged = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "오류 본문", showBackground = true, widthDp = 390, heightDp = 560)
@Composable
private fun DisplayErrorContentPreview() {
    ChalkakTheme {
        DisplayErrorContent(
            message = "전시를 불러오지 못했어요",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayScreenPreviewContent(uiState: DisplayUiState) {
    ChalkakTheme {
        DisplayScreen(
            uiState = uiState,
            onPreviousDateClick = {},
            onNextDateClick = {},
            onSortSelected = {},
            onFeaturedPageChanged = {},
            onOpenPhotoUpload = {},
            onNavigateToBottomBar = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
