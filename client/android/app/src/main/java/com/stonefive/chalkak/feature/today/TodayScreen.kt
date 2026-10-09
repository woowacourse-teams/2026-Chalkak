package com.stonefive.chalkak.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakFilledIconButton
import com.stonefive.chalkak.core.designsystem.component.empty.ChalkakEmptyPostContent
import com.stonefive.chalkak.core.designsystem.scroll.ChalkakScrollToTopButton
import com.stonefive.chalkak.core.designsystem.scroll.CollapsingScrollToTopThreshold
import com.stonefive.chalkak.core.designsystem.scroll.collapsingArea
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.today.component.TodayPhotoList
import com.stonefive.chalkak.feature.today.component.TodayTopBar
import com.stonefive.chalkak.feature.today.component.TodayTopic
import com.stonefive.chalkak.feature.today.component.todayBottomDivider
import java.time.LocalDate
import kotlinx.coroutines.launch

const val GUEST_LIKE_MESSAGE = "로그인 후 좋아요를 누를 수 있어요"
const val TODAY_ERROR_MESSAGE = "오늘을 불러오지 못했어요"
const val TODAY_REFRESH_CONTENT_DESCRIPTION = "홈 새로고침"
const val TODAY_LOADING_TEST_TAG = "home-loading"
const val TODAY_INITIAL_ERROR_TEST_TAG = "home-initial-error"
const val TODAY_EMPTY_TEST_TAG = "home-empty"
const val TODAY_NEXT_LOADING_TEST_TAG = "home-next-loading"

@Composable
fun TodayRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                TodayUiEvent.OpenPhotoUpload -> onOpenPhotoUpload()
                is TodayUiEvent.NavigateToBottomBar -> onNavigateToBottomBar(event.item)
            }
        }
    }

    TodayScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNotificationClick = onOpenNotifications,
    )
}

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onAction: (TodayUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (uiState.contentStatus != TodayContentStatus.Content) {
                ChalkakBottomBar(
                    selectedItem = ChalkakBottomBarItem.TODAY,
                    onItemSelected = { onAction(TodayUiAction.BottomBarSelected(it)) },
                    onAddClick = { onAction(TodayUiAction.AddClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState.contentStatus) {
                TodayContentStatus.Loading -> TodayInitialStatus(
                    status = uiState.contentStatus,
                    onRetryClick = { onAction(TodayUiAction.RetryClicked) },
                    onNotificationClick = onNotificationClick,
                    modifier = Modifier.fillMaxSize(),
                )

                is TodayContentStatus.Error -> TodayInitialStatus(
                    status = uiState.contentStatus,
                    onRetryClick = { onAction(TodayUiAction.RetryClicked) },
                    onNotificationClick = onNotificationClick,
                    modifier = Modifier.fillMaxSize(),
                )

                TodayContentStatus.Content -> TodayContent(
                    uiState = uiState,
                    onAction = onAction,
                    onNotificationClick = onNotificationClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TodayInitialStatus(
    status: TodayContentStatus,
    onRetryClick: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testTag = when (status) {
        TodayContentStatus.Loading -> TODAY_LOADING_TEST_TAG
        is TodayContentStatus.Error -> TODAY_INITIAL_ERROR_TEST_TAG
        TodayContentStatus.Content -> error("Content is rendered by TodayContent")
    }
    Column(modifier = modifier.statusBarsPadding()) {
        TodayTopBar(
            onNotificationClick = onNotificationClick,
            modifier = Modifier.fillMaxWidth().todayBottomDivider(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                TodayContentStatus.Loading -> {
                    CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                }

                is TodayContentStatus.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = status.reason.message,
                            color = ChalkakTheme.colors.textSecondary,
                            style = ChalkakTheme.typography.body,
                        )
                        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
                        ChalkakFilledIconButton(onClick = onRetryClick) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = TODAY_REFRESH_CONTENT_DESCRIPTION,
                            )
                        }
                    }
                }

                TodayContentStatus.Content -> Unit
            }
        }
    }
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onAction: (TodayUiAction) -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var localResetSignal by remember { mutableIntStateOf(0) }
    val interactionScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val fixedTopAreaHeightPx = statusBarHeightPx + with(density) {
        TodayTopBarHeight.toPx()
    }
    val scrollState = rememberTodayScrollBehaviorState(
        photoListState = photoListState,
        interactionScope = interactionScope,
        scrollToTopToggleThresholdPx = with(density) {
            CollapsingScrollToTopThreshold.toPx()
        },
    )
    val photoListTopPadding = with(density) {
        scrollState.visibleTopAreaHeight(fixedTopAreaHeightPx).toDp()
    }
    val bottomBarHeight = with(density) {
        scrollState.bottomBarState.height
            .toDp()
    }
    val topBarBackgroundAlpha = topBarBackgroundAlpha(scrollState.collapsedTopAreaProgress)

    LaunchedEffect(localResetSignal) {
        if (localResetSignal == 0) return@LaunchedEffect
        scrollState.reset()
        photoListState.animateScrollToItem(0)
    }

    LaunchedEffect(
        photoListState.canScrollBackward,
        photoListState.canScrollForward,
        scrollState.topAreaOffset,
    ) {
        if (!photoListState.canScrollBackward && !photoListState.canScrollForward) {
            scrollState.reset()
        } else {
            scrollState.updateScrollToTopVisibility()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground)
            .nestedScroll(scrollState.nestedScrollConnection),
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(TodayUiAction.RefreshRequested) },
            modifier = Modifier.fillMaxSize(),
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = photoListTopPadding),
                )
            },
        ) {
            TodayPhotoList(
                photos = uiState.photos,
                contentRevision = uiState.contentRevision,
                likedPhotoIds = uiState.likedPhotoIds,
                isLoadingNext = uiState.isLoadingNext,
                areLikesEnabled = uiState.areLikesEnabled,
                onLikeClick = { onAction(TodayUiAction.LikeClicked(it)) },
                onEndThresholdChanged = { onAction(TodayUiAction.EndThresholdChanged(it)) },
                modifier = Modifier.fillMaxSize(),
                state = photoListState,
                topContentPadding = photoListTopPadding,
            )
            if (uiState.photos.isEmpty()) {
                ChalkakEmptyPostContent(
                    modifier = Modifier.align(Alignment.Center),
                    testTag = TODAY_EMPTY_TEST_TAG,
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(TodayTopBarHeight))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .collapsingArea(scrollState.topAreaOffset),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChalkakBackground)
                        .onSizeChanged { scrollState.topAreaHeight = it.height },
                ) {
                    TodayTopic(
                        topicDate = uiState.topicDate,
                        topic = uiState.topic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(ChalkakBackground.copy(alpha = topBarBackgroundAlpha)),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
            TodayTopBar(
                onNotificationClick = onNotificationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollState.isTopAreaVisible) {
                            Modifier.todayBottomDivider()
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (scrollState.bottomBarState.isScrollToTopButtonVisible) {
            ChalkakScrollToTopButton(
                onClick = {
                    interactionScope.launch {
                        scrollState.reset()
                        photoListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 24.dp,
                        bottom = bottomBarHeight + 18.dp,
                    ),
            )
        }
        ChalkakBottomBar(
            selectedItem = ChalkakBottomBarItem.TODAY,
            onItemSelected = { item ->
                if (item == ChalkakBottomBarItem.TODAY) {
                    localResetSignal++
                }
                onAction(TodayUiAction.BottomBarSelected(item))
            },
            onAddClick = { onAction(TodayUiAction.AddClicked) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { scrollState.bottomBarState.height = it.height }
                .graphicsLayer { translationY = scrollState.bottomBarState.offset },
        )
    }
}

private val TodayTopBarHeight = 55.dp

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun TodayScreenPreview() {
    ChalkakTheme {
        TodayScreen(
            uiState = TodayUiState(
                contentStatus = TodayContentStatus.Content,
                topicDate = LocalDate.of(2026, 8, 3),
                topic = "하늘하늘하늘",
                photos = listOf(
                    Post(
                        id = "preview-1",
                        originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                        thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                        signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                        signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "노을이 진 하늘과 전신주",
                        title = "안녕하세요 찰캌입니다.",
                        likeCount = 24,
                    ),
                    Post(
                        id = "preview-2",
                        originalImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                        thumbnailImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                        signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                        signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "두 번째 사진",
                        title = null,
                        likeCount = 12,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"

val TodayInitialError.message: String
    get() = when (this) {
        TodayInitialError.TopicNotFound -> "오늘의 주제가 아직 준비되지 않았어요"
        TodayInitialError.Unauthorized -> "로그인 정보를 확인할 수 없어요"
        TodayInitialError.Network -> "네트워크 연결을 확인해 주세요"
        TodayInitialError.InvalidResponse -> "오늘 정보를 불러오지 못했어요"
        TodayInitialError.Client -> "요청을 처리하지 못했어요"
        TodayInitialError.Server -> "서버에 잠시 문제가 생겼어요"
        TodayInitialError.Generic -> TODAY_ERROR_MESSAGE
    }
