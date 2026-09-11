package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DisplayPhotoGrid(
    photos: List<Post>,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onPhotoClick: (Post) -> Unit = {},
    onEndThresholdChanged: (Boolean) -> Unit = {},
    isLoadingNext: Boolean = false,
    topContentPadding: Dp = 0.dp,
    horizontalContentPadding: Dp = 21.dp,
    verticalItemSpacing: Dp = 9.dp,
    header: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(state, photos.size) {
        snapshotFlow { state.isNearEnd(photos.size) }
            .distinctUntilChanged()
            .collect(onEndThresholdChanged)
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            start = horizontalContentPadding,
            top = topContentPadding + 4.dp,
            end = horizontalContentPadding,
            bottom = 36.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = verticalItemSpacing,
    ) {
        header?.let { content ->
            item(
                key = "featured",
                span = StaggeredGridItemSpan.FullLine,
            ) { content() }
        }
        items(
            items = photos,
            key = Post::id,
        ) { photo ->
            DisplayPhotoCard(
                photo = photo,
                onClick = { onPhotoClick(photo) },
            )
        }
        if (isLoadingNext) {
            item(
                key = NEXT_PAGE_LOADING_KEY,
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ChalkakTheme.spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                }
            }
        }
    }
}

private fun LazyStaggeredGridState.isNearEnd(totalItemCount: Int): Boolean {
    if (totalItemCount == 0) return false
    val lastVisibleIndex = layoutInfo.visibleItemsInfo
        .lastOrNull()
        ?.index
        ?: return false
    return lastVisibleIndex >= totalItemCount - END_THRESHOLD - 1
}

@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun DisplayPhotoGridPreview() {
    ChalkakTheme {
        DisplayPhotoGrid(
            photos = previewDisplayPhotos + previewDisplayPhotos.mapIndexed { index, post ->
                post.copy(id = "duplicate-$index")
            },
        )
    }
}

private const val END_THRESHOLD = 2
private const val NEXT_PAGE_LOADING_KEY = "display-next-page-loading"
