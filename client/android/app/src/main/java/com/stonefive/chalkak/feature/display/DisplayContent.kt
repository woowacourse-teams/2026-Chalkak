package com.stonefive.chalkak.feature.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.empty.ChalkakEmptyPostContent
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.display.component.DisplayFeaturedPager
import com.stonefive.chalkak.feature.display.component.DisplayPhotoGrid

@Composable
fun DisplayBody(
    content: DisplayContentState,
    onFeaturedPageChanged: (Int) -> Unit,
    onEndThresholdChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingNext: Boolean = false,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onPhotoClick: (Post) -> Unit = {},
    topContentPadding: Dp = 0.dp,
) {
    when (content) {
        DisplayContentState.Loading -> DisplayLoadingContent(
            modifier = modifier.padding(top = topContentPadding),
        )

        is DisplayContentState.Latest -> LatestDisplayContent(
            content = content,
            gridState = gridState,
            onPhotoClick = onPhotoClick,
            onEndThresholdChanged = onEndThresholdChanged,
            isLoadingNext = isLoadingNext,
            topContentPadding = topContentPadding,
            modifier = modifier,
        )

        is DisplayContentState.Archive -> ArchiveDisplayContent(
            content = content,
            onFeaturedPageChanged = onFeaturedPageChanged,
            gridState = gridState,
            onPhotoClick = onPhotoClick,
            onEndThresholdChanged = onEndThresholdChanged,
            isLoadingNext = isLoadingNext,
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
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onPhotoClick: (Post) -> Unit = {},
    onEndThresholdChanged: (Boolean) -> Unit = {},
    isLoadingNext: Boolean = false,
    topContentPadding: Dp = 0.dp,
) {
    if (content.photos.isEmpty()) {
        ChalkakEmptyPostContent(
            modifier = modifier,
            testTag = DISPLAY_EMPTY_TEST_TAG,
        )
    } else {
        Column(modifier = modifier) {
            DisplayPhotoGrid(
                photos = content.photos,
                state = gridState,
                onPhotoClick = onPhotoClick,
                onEndThresholdChanged = onEndThresholdChanged,
                isLoadingNext = isLoadingNext,
                topContentPadding = topContentPadding,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ArchiveDisplayContent(
    content: DisplayContentState.Archive,
    onFeaturedPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onPhotoClick: (Post) -> Unit = {},
    onEndThresholdChanged: (Boolean) -> Unit = {},
    isLoadingNext: Boolean = false,
    topContentPadding: Dp = 0.dp,
) {
    if (content.photos.isEmpty()) {
        ChalkakEmptyPostContent(
            modifier = modifier,
            testTag = DISPLAY_EMPTY_TEST_TAG,
            title = "이 날 올라온 사진이 없어요",
            description = null,
        )
    } else {
        DisplayPhotoGrid(
            photos = content.photos,
            state = gridState,
            onPhotoClick = onPhotoClick,
            onEndThresholdChanged = onEndThresholdChanged,
            isLoadingNext = isLoadingNext,
            topContentPadding = topContentPadding,
            horizontalContentPadding = 22.dp,
            verticalItemSpacing = 12.dp,
            header = {
                DisplayFeaturedPager(
                    photos = content.featuredPhotos,
                    selectedPage = content.featuredPage,
                    onPageChanged = onFeaturedPageChanged,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            },
            modifier = modifier,
        )
    }
}

const val DISPLAY_EMPTY_TEST_TAG = "display-empty"

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
