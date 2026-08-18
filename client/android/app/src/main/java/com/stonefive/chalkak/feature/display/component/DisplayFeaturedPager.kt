package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DisplayFeaturedPager(
    photos: List<Post>,
    selectedPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) return

    val virtualPageCount = if (photos.size > 1) Int.MAX_VALUE else 1
    val middlePage = virtualPageCount / 2
    val alignedMiddlePage = middlePage - middlePage % photos.size
    val pagerState = rememberPagerState(
        initialPage = alignedMiddlePage + selectedPage.coerceIn(0, photos.lastIndex),
        pageCount = { virtualPageCount },
    )

    LaunchedEffect(pagerState, photos.size) {
        snapshotFlow { pagerState.settledPage % photos.size }
            .distinctUntilChanged()
            .collect(onPageChanged)
    }

    LaunchedEffect(selectedPage, photos.size) {
        val targetPhotoIndex = selectedPage.coerceIn(0, photos.lastIndex)
        val currentPhotoIndex = pagerState.currentPage % photos.size
        if (currentPhotoIndex != targetPhotoIndex) {
            val currentCycleStart = pagerState.currentPage - currentPhotoIndex
            val targetPage = listOf(
                currentCycleStart + targetPhotoIndex,
                currentCycleStart + targetPhotoIndex - photos.size,
                currentCycleStart + targetPhotoIndex + photos.size,
            ).filter { it in 0 until virtualPageCount }
                .minBy { abs(it - pagerState.currentPage) }
            pagerState.animateScrollToPage(targetPage)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val containerWidth = maxWidth
        val pageWidth = containerWidth - 64.dp

        Column(modifier = Modifier.width(containerWidth)) {
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 5.dp,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(containerWidth),
            ) { page ->
                val photo = photos[page % photos.size]
                val pageOffset = (
                    pagerState.currentPage - page + pagerState.currentPageOffsetFraction
                    ).absoluteValue.coerceIn(0f, 1f)

                DisplayPhotoCard(
                    photo = photo,
                    variant = DisplayPhotoCardVariant.FEATURED,
                    modifier = Modifier
                        .aspectRatio(3f / 4f)
                        .graphicsLayer {
                            val scale = 1f - pageOffset * 0.08f
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - pageOffset * 0.12f
                        },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            DisplayPageIndicator(
                pageCount = photos.size,
                selectedPage = pagerState.currentPage % photos.size,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun DisplayPageIndicator(
    pageCount: Int,
    selectedPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .size(
                        width = if (page == selectedPage) 24.dp else 7.dp,
                        height = 7.dp,
                    ).clip(ChalkakTheme.shapes.pill)
                    .background(
                        if (page == selectedPage) {
                            ChalkakTheme.colors.actionPrimary
                        } else {
                            ChalkakTheme.colors.textMuted
                                .copy(alpha = 0.45f)
                        },
                    ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DisplayFeaturedPagerPreview() {
    ChalkakTheme {
        DisplayFeaturedPager(
            photos = previewDisplayPhotos,
            selectedPage = 0,
            onPageChanged = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisplayPageIndicatorPreview() {
    ChalkakTheme {
        DisplayPageIndicator(
            pageCount = 5,
            selectedPage = 0,
        )
    }
}
