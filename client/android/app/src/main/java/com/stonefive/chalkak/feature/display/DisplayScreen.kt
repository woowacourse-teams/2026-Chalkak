package com.stonefive.chalkak.feature.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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

@Composable
fun DisplayRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DisplayViewModel = viewModel(factory = DisplayViewModel.Factory),
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
) {
    Scaffold(
        modifier = modifier,
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.DISPLAY,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding(),
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

            DisplayBody(
                content = uiState.content,
                onSortSelected = onSortSelected,
                onFeaturedPageChanged = onFeaturedPageChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DisplayBody(
    content: DisplayContentState,
    onSortSelected: (PostSort) -> Unit,
    onFeaturedPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        DisplayContentState.Loading -> DisplayLoadingContent(modifier = modifier)

        is DisplayContentState.Latest -> LatestDisplayContent(
            content = content,
            onSortSelected = onSortSelected,
            modifier = modifier,
        )

        is DisplayContentState.Archive -> ArchiveDisplayContent(
            content = content,
            onFeaturedPageChanged = onFeaturedPageChanged,
            modifier = modifier,
        )

        is DisplayContentState.Error -> DisplayErrorContent(
            message = content.message,
            modifier = modifier,
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
    onSortSelected: (PostSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyStaggeredGridState()

    LaunchedEffect(content.selectedSort) {
        gridState.scrollToItem(0)
    }

    Column(modifier = modifier) {
        DisplaySortTabs(
            selectedSort = content.selectedSort,
            onSortSelected = onSortSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        DisplayPhotoGrid(
            photos = content.photos,
            state = gridState,
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
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 22.dp,
            top = 4.dp,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
        items(
            items = content.photos,
            key = Post::id,
        ) { photo ->
            DisplayPhotoCard(photo = photo)
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
            onSortSelected = {},
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
            onSortSelected = {},
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
