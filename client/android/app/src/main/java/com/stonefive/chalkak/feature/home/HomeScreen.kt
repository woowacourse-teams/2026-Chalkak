package com.stonefive.chalkak.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.home.component.HomeHeroCard
import com.stonefive.chalkak.feature.home.component.HomeIssueSection
import com.stonefive.chalkak.feature.home.component.HomeRankingRow
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopBarHeight
import com.stonefive.chalkak.feature.home.component.HomeWeeklyRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val HOME_CONTENT_TEST_TAG = "home-content"

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenPhotoUpload: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenDisplay: (LocalDate) -> Unit,
    onOpenFeed: (Post) -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onRetry: () -> Unit,
    onNotificationClick: () -> Unit,
    onIssueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.TODAY,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ChalkakTheme.colors.background),
        ) {
            HomeTopBar(
                onNotificationClick = onNotificationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(HomeTopBarHeight)
                    .homeBottomDivider()
                    .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
            )
            HomeContent(
                uiState = uiState,
                onOpenPhotoUpload = onOpenPhotoUpload,
                onOpenRecord = onOpenRecord,
                onOpenDisplay = onOpenDisplay,
                onOpenFeed = onOpenFeed,
                onRetry = onRetry,
                onIssueClick = onIssueClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onOpenPhotoUpload: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenDisplay: (LocalDate) -> Unit,
    onOpenFeed: (Post) -> Unit,
    onRetry: () -> Unit,
    onIssueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRetry,
        modifier = modifier,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(HOME_CONTENT_TEST_TAG),
            contentPadding = PaddingValues(bottom = ChalkakTheme.spacing.xxl),
        ) {
            item {
                HomeTopicHeader(
                    date = uiState.date,
                    topic = uiState.topic,
                    topicStatus = uiState.topicStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ChalkakTheme.spacing.screenHorizontal,
                            top = ChalkakTheme.spacing.xl + ChalkakTheme.spacing.sm,
                            end = ChalkakTheme.spacing.screenHorizontal,
                        ),
                )
            }
            item {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
                HomeHeroCard(
                    hero = uiState.hero,
                    topic = uiState.topic,
                    onOpenPhotoUpload = onOpenPhotoUpload,
                    onOpenRecord = onOpenRecord,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
            }
            item {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                HomeWeeklyRecord(
                    days = uiState.week,
                    status = uiState.recordsStatus,
                    onOpenRecord = onOpenRecord,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
            }
            item {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                HomeRankingRow(
                    title = "어제 랭킹",
                    ranking = uiState.yesterday,
                    displayDate = uiState.date.minusDays(1),
                    onOpenDisplay = onOpenDisplay,
                    onOpenFeed = onOpenFeed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                HomeRankingRow(
                    title = "오늘 인기있는 사진",
                    ranking = uiState.trending,
                    displayDate = uiState.date,
                    onOpenDisplay = onOpenDisplay,
                    onOpenFeed = onOpenFeed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
                HomeIssueSection(
                    onIssueClick = onIssueClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
            }
        }
    }
}

@Composable
private fun HomeTopicHeader(
    date: LocalDate,
    topic: String,
    topicStatus: HomeSectionStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "${date.format(HomeDateFormatter)} · 오늘의 주제",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        Text(
            text = when (topicStatus) {
                HomeSectionStatus.Loading -> "오늘의 주제를 불러오는 중"
                HomeSectionStatus.Error -> "오늘의 주제를 불러오지 못했어요"
                HomeSectionStatus.Ready -> topic.ifBlank { "오늘의 주제" }
            },
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2,
        )
    }
}

@Composable
private fun Modifier.homeBottomDivider(): Modifier {
    val borderColor = ChalkakTheme.colors.border
    return drawBehind {
        val strokeWidth = HomeDividerWidth.toPx()
        drawLine(
            color = borderColor,
            start = Offset(0f, size.height - strokeWidth / 2),
            end = Offset(size.width, size.height - strokeWidth / 2),
            strokeWidth = strokeWidth,
        )
    }
}

private val HomeDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val HomeDividerWidth = 0.5.dp

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun HomeScreenPreview() {
    val date = LocalDate.of(2026, 8, 3)
    ChalkakTheme {
        HomeScreen(
            uiState = HomeUiState(
                date = date,
                topic = "하늘",
                topicStatus = HomeSectionStatus.Ready,
                hero = HomeHeroState.Photo(
                    imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    thumbnailUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    signatureThumbnailUrl = drawableResourceUrl(R.drawable.preview_signature),
                ),
                week = homeWeekDates(date).mapIndexed { index, day ->
                    HomeWeekDay(
                        date = day,
                        post = if (index in listOf(0, 2, 3, 5, 6)) {
                            PostCalendarItem(
                                postId = "week-$index",
                                topicDate = day,
                                thumbnailImageUrl = drawableResourceUrl(
                                    if (index % 2 == 0) R.drawable.preview_photo else R.drawable.home_feed_photo,
                                ),
                                status = if (index == 3) PostStatus.PENDING else PostStatus.APPROVED,
                            )
                        } else {
                            null
                        },
                    )
                },
                recordsStatus = HomeSectionStatus.Ready,
                yesterday = HomeRankingState(HomeSectionStatus.Ready, previewHomePosts()),
                trending = HomeRankingState(HomeSectionStatus.Ready, previewHomePosts().reversed()),
                isAuthenticated = true,
            ),
            onOpenPhotoUpload = {},
            onOpenRecord = {},
            onOpenDisplay = {},
            onOpenFeed = {},
            onNavigateToBottomBar = {},
            onRetry = {},
            onNotificationClick = {},
            onIssueClick = {},
        )
    }
}

private fun previewHomePosts(): List<Post> = listOf(
    previewHomePost("1", R.drawable.preview_photo, "등대"),
    previewHomePost("2", R.drawable.home_feed_photo, "노을"),
    previewHomePost("3", R.drawable.record_landscape_photo, "산"),
    previewHomePost("4", R.drawable.preview_photo, "바다"),
)

private fun previewHomePost(
    id: String,
    imageRes: Int,
    title: String,
): Post = Post(
    id = "preview-$id",
    originalImageUrl = drawableResourceUrl(imageRes),
    thumbnailImageUrl = drawableResourceUrl(imageRes),
    signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
    signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
    contentDescription = "$title 사진",
    title = title,
    likeCount = id.toInt(),
)

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
