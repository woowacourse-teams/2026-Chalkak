package com.stonefive.chalkak.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakScreenBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.rememberScreenBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.component.image.photoTransitionChrome
import com.stonefive.chalkak.core.designsystem.scroll.ChalkakScrollToTopButton
import com.stonefive.chalkak.core.designsystem.scroll.CollapsingScrollToTopThreshold
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.aireview.component.AiReviewSheetDragHandle
import com.stonefive.chalkak.feature.home.component.HomeFeedbackCard
import com.stonefive.chalkak.feature.home.component.HomeFeedbackStartSheet
import com.stonefive.chalkak.feature.home.component.HomeHeroCard
import com.stonefive.chalkak.feature.home.component.HomeIssueSection
import com.stonefive.chalkak.feature.home.component.HomeRankingRow
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopBarHeight
import com.stonefive.chalkak.feature.home.component.HomeWeeklyRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    onIssueClick: (title: String, url: String) -> Unit,
    onStartFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMoreBanners: () -> Unit = {},
    onOpenRecordDate: (LocalDate) -> Unit = {},
    onDismissFeedbackMessage: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    // 날짜가 바뀌면 다른 사진의 카드이므로 펼침·시트 상태를 처음으로 되돌린다.
    val feedbackIdentity = uiState.date to (uiState.hero as? HomeHeroState.Photo)?.postId
    var isFeedbackExpanded by rememberSaveable(feedbackIdentity) { mutableStateOf(false) }
    var isFeedbackSheetVisible by rememberSaveable(feedbackIdentity) { mutableStateOf(false) }
    val bottomBarState = rememberScreenBottomBarScrollState(ChalkakBottomBarItem.HOME)
    val interactionScope = rememberCoroutineScope()
    val scrollToTopToggleThresholdPx = with(LocalDensity.current) {
        CollapsingScrollToTopThreshold.toPx()
    }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarState.height.toDp() }
    val nestedScrollConnection = remember(
        listState,
        bottomBarState,
        scrollToTopToggleThresholdPx,
        interactionScope,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    bottomBarState.onScroll(
                        scrollDeltaY = available.y,
                        atTop = !listState.canScrollBackward,
                        toggleThresholdPx = scrollToTopToggleThresholdPx,
                    )
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                bottomBarState.settle(interactionScope)
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(listState.canScrollBackward) {
        if (!listState.canScrollBackward) {
            bottomBarState.hideScrollToTopButton()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
        containerColor = ChalkakTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChalkakTheme.colors.background),
            ) {
                HomeTopBar(
                    onNotificationClick = onNotificationClick,
                    hasUnreadNotifications = uiState.hasUnreadNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .photoTransitionChrome()
                        .background(ChalkakTheme.colors.background)
                        .statusBarsPadding()
                        .height(HomeTopBarHeight)
                        .homeBottomDivider()
                        .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
                HomeContent(
                    uiState = uiState,
                    onOpenPhotoUpload = onOpenPhotoUpload,
                    onOpenRecord = onOpenRecord,
                    onOpenRecordDate = onOpenRecordDate,
                    onOpenDisplay = onOpenDisplay,
                    onOpenFeed = onOpenFeed,
                    onRetry = onRetry,
                    onIssueClick = onIssueClick,
                    isFeedbackExpanded = isFeedbackExpanded,
                    onFeedbackStartClick = {
                        // 실패한 리뷰는 시작 안내 없이 바로 다시 요청한다.
                        if (uiState.feedbackPhase == HomeFeedbackPhase.Failed) {
                            onStartFeedback()
                        } else {
                            isFeedbackSheetVisible = true
                        }
                    },
                    onFeedbackToggleExpanded = { isFeedbackExpanded = !isFeedbackExpanded },
                    onLoadMoreBanners = onLoadMoreBanners,
                    listState = listState,
                    bottomBarHeight = bottomBarHeight,
                    modifier = Modifier.weight(1f),
                )
            }
            if (bottomBarState.isScrollToTopButtonVisible) {
                ChalkakScrollToTopButton(
                    onClick = {
                        interactionScope.launch {
                            listState.animateScrollToTopSmoothly()
                            bottomBarState.reset()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .photoTransitionChrome()
                        .padding(
                            end = 24.dp,
                            bottom = bottomBarHeight + 18.dp,
                        ),
                )
            }
            ChalkakScreenBottomBar(
                selectedItem = ChalkakBottomBarItem.HOME,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onOpenPhotoUpload,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                scrollState = bottomBarState,
            )
        }
    }
    if (isFeedbackSheetVisible) {
        HomeFeedbackStartBottomSheet(
            onStart = {
                isFeedbackSheetVisible = false
                onStartFeedback()
            },
            onDismiss = { isFeedbackSheetVisible = false },
        )
    }
    uiState.feedbackMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissFeedbackMessage,
            confirmButton = {
                TextButton(onClick = onDismissFeedbackMessage) {
                    Text("확인", color = ChalkakTheme.colors.actionPrimary, style = ChalkakTheme.typography.callout)
                }
            },
            text = {
                Text(message, color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.callout)
            },
            shape = ChalkakTheme.shapes.large,
            containerColor = ChalkakTheme.colors.surfaceElevated,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedbackStartBottomSheet(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        // 화면 아래 끝과 좌우를 꽉 채우도록 위쪽 모서리만 둥글게 하고 기본 최대 너비(640dp) 제한을 푼다.
        sheetMaxWidth = Dp.Unspecified,
        shape = ChalkakTheme.shapes.sheet
            .copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = ChalkakTheme.colors.surfaceElevated,
    ) {
        AiReviewSheetDragHandle(sheetState = sheetState, onDismiss = onDismiss, modifier = Modifier.fillMaxWidth())
        HomeFeedbackStartSheet(
            onStart = {
                // 시트가 내려가는 애니메이션을 마친 뒤 닫힌 상태로 바꿔야 시트가 뚝 끊기지 않는다.
                // 그 사이 날짜가 바뀌어 시트가 화면에서 사라지면 코루틴이 취소되어 이전 날짜의 분석을 시작하지 않는다.
                scope.launch {
                    sheetState.hide()
                    onStart()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChalkakTheme.spacing.xl,
                    top = ChalkakTheme.spacing.sm,
                    end = ChalkakTheme.spacing.xl,
                    bottom = ChalkakTheme.spacing.xl,
                ),
        )
    }
}

private suspend fun LazyListState.animateScrollToTopSmoothly() {
    for (index in firstVisibleItemIndex downTo 0) {
        animateScrollToItem(index)
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
    onIssueClick: (title: String, url: String) -> Unit,
    isFeedbackExpanded: Boolean,
    onOpenRecordDate: (LocalDate) -> Unit,
    onFeedbackStartClick: () -> Unit,
    onFeedbackToggleExpanded: () -> Unit,
    onLoadMoreBanners: () -> Unit,
    listState: LazyListState,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnLoadMoreBanners by rememberUpdatedState(onLoadMoreBanners)
    LaunchedEffect(listState) {
        snapshotFlow {
            val state = currentUiState
            val lastVisibleIssue = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? HomeIssueItemKey }
                .maxOfOrNull { it.index }
            state.banners.isNotEmpty() &&
                state.bannerStatus == HomeSectionStatus.Ready &&
                state.bannerHasNext &&
                !state.isRefreshing &&
                !state.isLoadingMoreBanners &&
                !state.bannerLoadMoreError &&
                lastVisibleIssue != null &&
                lastVisibleIssue >= state.banners.lastIndex - ISSUE_PREFETCH_DISTANCE
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore) currentOnLoadMoreBanners()
        }
    }

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
            contentPadding = PaddingValues(bottom = bottomBarHeight + ChalkakTheme.spacing.xxl),
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
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
            }
            if (uiState.showsFeedback) {
                item {
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
                    HomeFeedbackCard(
                        phase = uiState.feedbackPhase,
                        result = uiState.feedbackResult,
                        canRequestAgain = uiState.feedbackCanRequestAgain,
                        isExpanded = isFeedbackExpanded,
                        onStart = onFeedbackStartClick,
                        onToggleExpanded = onFeedbackToggleExpanded,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                    )
                }
            }
            item {
                Spacer(
                    modifier = Modifier.height(
                        if (uiState.showsFeedback) ChalkakTheme.spacing.xl else ChalkakTheme.spacing.xxl,
                    ),
                )
                HomeWeeklyRecord(
                    days = uiState.week,
                    status = uiState.recordsStatus,
                    onOpenRecord = onOpenRecord,
                    onOpenRecordDate = onOpenRecordDate,
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
            if (uiState.banners.isNotEmpty() || uiState.bannerStatus == HomeSectionStatus.Loading) {
                item(key = "issue-header") {
                    Text(
                        text = "ISSUE",
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.headline,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ChalkakTheme.spacing.screenHorizontal,
                                top = ChalkakTheme.spacing.xxl,
                                end = ChalkakTheme.spacing.screenHorizontal,
                            ),
                    )
                }
                if (uiState.banners.isEmpty()) {
                    item(key = "issue-loading") {
                        HomeIssueSection(
                            banner = null,
                            onIssueClick = onIssueClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                                .padding(top = ChalkakTheme.spacing.lg),
                            showHeader = false,
                        )
                    }
                }
                itemsIndexed(
                    items = uiState.banners,
                    key = { index, banner -> HomeIssueItemKey(banner.id, index) },
                ) { index, banner ->
                    HomeIssueSection(
                        banner = banner,
                        onIssueClick = onIssueClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                            .padding(top = if (index == 0) ChalkakTheme.spacing.lg else ChalkakTheme.spacing.xxl),
                        showHeader = false,
                    )
                }
                if (uiState.isLoadingMoreBanners || uiState.bannerLoadMoreError) {
                    item(key = "issue-pagination") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ChalkakTheme.spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uiState.isLoadingMoreBanners) {
                                CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                            } else {
                                TextButton(onClick = onLoadMoreBanners) {
                                    Text(
                                        text = "다시 시도",
                                        color = ChalkakTheme.colors.actionPrimary,
                                        style = ChalkakTheme.typography.body,
                                    )
                                }
                            }
                        }
                    }
                }
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
                HomeSectionStatus.Ready -> topic.ifBlank { "오늘의 주제가 아직 준비되지 않았어요" }
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

private const val ISSUE_PREFETCH_DISTANCE = 2

private data class HomeIssueItemKey(
    val id: String,
    val index: Int,
) : java.io.Serializable

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
            onIssueClick = { _, _ -> },
            onStartFeedback = {},
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
    authorId = "preview-author",
)

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
