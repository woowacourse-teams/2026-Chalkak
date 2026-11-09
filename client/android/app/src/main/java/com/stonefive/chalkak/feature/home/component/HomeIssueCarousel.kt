package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Banner

/**
 * ISSUE를 가로로 넘겨 보는 캐러셀. 넘길 수 있다는 걸 알 수 있도록 다음 카드를 [IssueCardPeek]만큼 보여 준다.
 * [pagerState]의 페이지 수가 [banners]보다 많으면 마지막 페이지에 추가 로딩·재시도 카드를 둔다.
 */
@Composable
fun HomeIssueCarousel(
    banners: List<Banner>,
    pagerState: PagerState,
    isLoadingMore: Boolean,
    onIssueClick: (title: String, url: String) -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    onIssueTap: (banner: Banner, page: Int) -> Unit = { _, _ -> },
) {
    val screenHorizontal = ChalkakTheme.spacing.screenHorizontal
    val pageSpacing = ChalkakTheme.spacing.md
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = screenHorizontal,
            end = if (pagerState.pageCount > 1) pageSpacing + IssueCardPeek else screenHorizontal,
        ),
        pageSpacing = pageSpacing,
        verticalAlignment = Alignment.Top,
    ) { page ->
        val banner = banners.getOrNull(page)
        if (banner != null) {
            HomeIssueSection(
                banner = banner,
                onIssueClick = { title, url ->
                    onIssueTap(banner, page)
                    onIssueClick(title, url)
                },
                modifier = Modifier.fillMaxWidth(),
                showHeader = false,
                textMaxLines = ISSUE_CARD_TEXT_MAX_LINES,
            )
        } else {
            HomeIssuePaginationCard(
                isLoadingMore = isLoadingMore,
                onRetry = onRetryLoadMore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeIssuePaginationCard(
    isLoadingMore: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(ISSUE_IMAGE_ASPECT_RATIO)
            .clip(ChalkakTheme.shapes.large)
            .background(ChalkakTheme.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
        } else {
            TextButton(onClick = onRetry) {
                Text(
                    text = "다시 시도",
                    color = ChalkakTheme.colors.actionPrimary,
                    style = ChalkakTheme.typography.body,
                )
            }
        }
    }
}

private val IssueCardPeek = 32.dp

// 카드 높이가 페이지마다 달라지지 않도록 제목과 부제를 한 줄로 고정한다.
private const val ISSUE_CARD_TEXT_MAX_LINES = 1

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeIssueCarouselPreview() {
    val banners = List(3) { index ->
        Banner(
            id = "preview-banner-$index",
            title = "함께 만들어주신 찰캌",
            subtitle = "베타테스트에 함께해주셔서 감사합니다",
            webViewUrl = "https://example.com/issue",
            thumbnailUrl = "https://example.com/banner.jpg",
            createdAt = "2026-10-20T08:08:11.001Z",
        )
    }
    ChalkakTheme {
        HomeIssueCarousel(
            banners = banners,
            pagerState = rememberPagerState { banners.size },
            isLoadingMore = false,
            onIssueClick = { _, _ -> },
            onRetryLoadMore = {},
        )
    }
}
