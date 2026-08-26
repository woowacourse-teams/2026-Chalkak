package com.stonefive.chalkak.feature.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.display.component.DisplayDateHeader
import com.stonefive.chalkak.feature.display.component.DisplayFeaturedPager
import com.stonefive.chalkak.feature.display.component.DisplayPhotoCard
import com.stonefive.chalkak.feature.display.component.DisplayPhotoGrid
import com.stonefive.chalkak.feature.display.component.DisplaySortTabs
import com.stonefive.chalkak.feature.display.component.previewDisplayPhotos
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var headerOffset by remember { mutableFloatStateOf(0f) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var isHeaderTargetHidden by remember { mutableStateOf(false) }
    var filterOffset by remember { mutableFloatStateOf(0f) }
    var filterHeight by remember { mutableIntStateOf(0) }
    var isFilterTargetHidden by remember { mutableStateOf(false) }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    val settleScope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val bottomBarRestoreJob = remember { mutableStateOf<Job?>(null) }
    val selectedSort = (uiState.content as? DisplayContentState.Latest)?.selectedSort
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val visibleTopAreaHeightPx = (
        statusBarHeightPx +
            headerHeight + headerOffset +
            filterHeight + filterOffset
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

    val nestedScrollConnection = remember(
        headerHeight,
        filterHeight,
        uiState.content is DisplayContentState.Latest,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y != 0f) {
                        bottomBarRestoreJob.value?.cancel()
                        isBottomBarVisible = false
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

                    if (available.y > 0f) {
                        settleJob.value?.cancel()
                        var remainingScroll = available.y
                        var consumedScroll = 0f

                        if (uiState.content is DisplayContentState.Latest &&
                            filterHeight > 0
                        ) {
                            val previousOffset = filterOffset
                            filterOffset = (filterOffset + remainingScroll).coerceAtMost(0f)
                            val consumedByFilter = filterOffset - previousOffset
                            consumedScroll += consumedByFilter
                            remainingScroll -= consumedByFilter
                        }

                        if (remainingScroll > 0f && headerHeight > 0) {
                            val previousOffset = headerOffset
                            headerOffset = (headerOffset + remainingScroll).coerceAtMost(0f)
                            consumedScroll += headerOffset - previousOffset
                        }

                        if (consumedScroll != 0f) {
                            return Offset(0f, consumedScroll)
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settleTopAreas()
                bottomBarRestoreJob.value?.cancel()
                bottomBarRestoreJob.value = settleScope.launch {
                    delay(BOTTOM_BAR_RESTORE_DELAY_MILLIS)
                    isBottomBarVisible = true
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(uiState.selectedDate, selectedSort) {
        settleJob.value?.cancel()
        bottomBarRestoreJob.value?.cancel()
        headerOffset = 0f
        isHeaderTargetHidden = false
        filterOffset = 0f
        isFilterTargetHidden = false
        isBottomBarVisible = true
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
        AnimatedVisibility(
            visible = isBottomBarVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
            exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
        ) {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.DISPLAY,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Modifier.collapsingArea(offset: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val offsetPx = offset.toInt().coerceIn(-placeable.height, 0)
    val visibleHeight = (placeable.height + offsetPx).coerceAtLeast(0)

    layout(placeable.width, visibleHeight) {
        placeable.placeRelative(0, offsetPx)
    }
}

internal fun settleDisplayAreaOffset(
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

private const val BOTTOM_BAR_RESTORE_DELAY_MILLIS = 500L
private const val DISPLAY_FLOATING_BACKGROUND_ALPHA = 0.86f

@Composable
fun DisplayBody(
    content: DisplayContentState,
    onFeaturedPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPhotoClick: (Post) -> Unit = {},
    topContentPadding: Dp = 0.dp,
) {
    when (content) {
        DisplayContentState.Loading -> DisplayLoadingContent(
            modifier = modifier.padding(top = topContentPadding),
        )

        is DisplayContentState.Latest -> LatestDisplayContent(
            content = content,
            onPhotoClick = onPhotoClick,
            topContentPadding = topContentPadding,
            modifier = modifier,
        )

        is DisplayContentState.Archive -> ArchiveDisplayContent(
            content = content,
            onFeaturedPageChanged = onFeaturedPageChanged,
            onPhotoClick = onPhotoClick,
            topContentPadding = topContentPadding,
            modifier = modifier,
        )

        is DisplayContentState.Error -> DisplayErrorContent(
            message = content.message,
            modifier = modifier.padding(top = topContentPadding),
        )
    }
}

@Composable
fun DisplayLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        CircularProgressIndicator(
            color = ChalkakTheme.colors.actionPrimary,
            modifier = Modifier.padding(top = 64.dp),
        )
    }
}

@Composable
fun LatestDisplayContent(
    content: DisplayContentState.Latest,
    modifier: Modifier = Modifier,
    onPhotoClick: (Post) -> Unit = {},
    topContentPadding: Dp = 0.dp,
) {
    val gridState = rememberLazyStaggeredGridState()

    LaunchedEffect(content.selectedSort) {
        gridState.scrollToItem(0)
    }

    Column(modifier = modifier) {
        DisplayPhotoGrid(
            photos = content.photos,
            state = gridState,
            onPhotoClick = onPhotoClick,
            topContentPadding = topContentPadding,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
fun ArchiveDisplayContent(
    content: DisplayContentState.Archive,
    onFeaturedPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPhotoClick: (Post) -> Unit = {},
    topContentPadding: Dp = 0.dp,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 22.dp,
            top = topContentPadding + 4.dp,
            end = 22.dp,
            bottom = 36.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(
            key = "featured",
            span = StaggeredGridItemSpan.FullLine,
        ) {
            DisplayFeaturedPager(
                photos = content.featuredPhotos,
                selectedPage = content.featuredPage,
                onPageChanged = onFeaturedPageChanged,
                onPhotoClick = onPhotoClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
        items(
            items = content.photos,
            key = Post::id,
        ) { photo ->
            DisplayPhotoCard(
                photo = photo,
                onClick = { onPhotoClick(photo) },
            )
        }
    }
}

@Composable
fun DisplayErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
        )
    }
}

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

private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"
