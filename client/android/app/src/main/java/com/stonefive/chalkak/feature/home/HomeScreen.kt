package com.stonefive.chalkak.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.stonefive.chalkak.core.designsystem.scroll.ChalkakScrollToTopButton
import com.stonefive.chalkak.core.designsystem.scroll.CollapsingScrollToTopThreshold
import com.stonefive.chalkak.core.designsystem.scroll.collapsingArea
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.component.HomePhotoList
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopic
import com.stonefive.chalkak.feature.home.component.homeBottomDivider
import kotlinx.coroutines.launch

internal const val GUEST_LIKE_MESSAGE = "로그인 후 좋아요를 누를 수 있어요"
internal const val HOME_ERROR_MESSAGE = "홈을 불러오지 못했어요"
internal const val HOME_EMPTY_TITLE = "아직 올라온 사진이 없어요"
internal const val HOME_EMPTY_DESCRIPTION = "첫 번째 사진을 올려보세요"
internal const val HOME_REFRESH_CONTENT_DESCRIPTION = "홈 새로고침"
internal const val HOME_LOADING_TEST_TAG = "home-loading"
internal const val HOME_INITIAL_ERROR_TEST_TAG = "home-initial-error"
internal const val HOME_EMPTY_TEST_TAG = "home-empty"
internal const val HOME_NEXT_LOADING_TEST_TAG = "home-next-loading"

@Composable
fun HomeRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    selectionSignal: Int = 0,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                HomeUiEvent.OpenPhotoUpload -> onOpenPhotoUpload()

                HomeUiEvent.ShowGuestLikeMessage -> launch {
                    snackbarHostState.showSnackbar(GUEST_LIKE_MESSAGE)
                }

                is HomeUiEvent.ShowRefreshFailure -> launch {
                    snackbarHostState.showSnackbar(event.reason.message)
                }

                is HomeUiEvent.NavigateToBottomBar -> onNavigateToBottomBar(event.item)
            }
        }
    }

    LaunchedEffect(selectionSignal) {
        if (selectionSignal > 0) {
            viewModel.onAction(HomeUiAction.RefreshRequested)
        }
    }

    HomeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        resetSignal = selectionSignal,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.contentStatus != HomeContentStatus.Content) {
                ChalkakBottomBar(
                    selectedItem = ChalkakBottomBarItem.TODAY,
                    onItemSelected = { onAction(HomeUiAction.BottomBarSelected(it)) },
                    onAddClick = { onAction(HomeUiAction.AddClicked) },
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
                HomeContentStatus.Loading -> HomeInitialStatus(
                    status = uiState.contentStatus,
                    onRetryClick = { onAction(HomeUiAction.RetryClicked) },
                    modifier = Modifier.fillMaxSize(),
                )

                is HomeContentStatus.Error -> HomeInitialStatus(
                    status = uiState.contentStatus,
                    onRetryClick = { onAction(HomeUiAction.RetryClicked) },
                    modifier = Modifier.fillMaxSize(),
                )

                HomeContentStatus.Content -> HomeContent(
                    uiState = uiState,
                    onAction = onAction,
                    resetSignal = resetSignal,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeInitialStatus(
    status: HomeContentStatus,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testTag = when (status) {
        HomeContentStatus.Loading -> HOME_LOADING_TEST_TAG
        is HomeContentStatus.Error -> HOME_INITIAL_ERROR_TEST_TAG
        HomeContentStatus.Content -> error("Content is rendered by HomeContent")
    }
    Column(modifier = modifier.statusBarsPadding()) {
        HomeTopBar(modifier = Modifier.homeBottomDivider())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                HomeContentStatus.Loading -> {
                    CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
                }

                is HomeContentStatus.Error -> {
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
                                contentDescription = HOME_REFRESH_CONTENT_DESCRIPTION,
                            )
                        }
                    }
                }

                HomeContentStatus.Content -> Unit
            }
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    resetSignal: Int,
    modifier: Modifier = Modifier,
) {
    val photoListState = rememberLazyListState()
    var localResetSignal by remember { mutableIntStateOf(0) }
    val interactionScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    val fixedTopAreaHeightPx = statusBarHeightPx + with(density) {
        HomeTopBarHeight.toPx()
    }
    val scrollState = rememberHomeScrollBehaviorState(
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

    LaunchedEffect(uiState.selectedSort, uiState.refreshRevision) {
        scrollState.reset()
        photoListState.scrollToItem(0)
    }

    LaunchedEffect(resetSignal, localResetSignal) {
        if (resetSignal == 0 && localResetSignal == 0) return@LaunchedEffect
        scrollState.reset()
        photoListState.animateScrollToItem(0)
    }

    LaunchedEffect(photoListState.canScrollBackward, scrollState.topAreaOffset) {
        scrollState.updateScrollToTopVisibility()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground)
            .nestedScroll(scrollState.nestedScrollConnection),
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeUiAction.RefreshRequested) },
            modifier = Modifier.fillMaxSize(),
        ) {
            HomePhotoList(
                photos = uiState.photos,
                likedPhotoIds = uiState.likedPhotoIds,
                isLoadingNext = uiState.isLoadingNext,
                areLikesEnabled = uiState.areLikesEnabled,
                onLikeClick = { onAction(HomeUiAction.LikeClicked(it)) },
                onEndThresholdChanged = { onAction(HomeUiAction.EndThresholdChanged(it)) },
                modifier = Modifier.fillMaxSize(),
                state = photoListState,
                topContentPadding = photoListTopPadding,
            )
            if (uiState.photos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag(HOME_EMPTY_TEST_TAG),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = HOME_EMPTY_TITLE,
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.body,
                    )
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
                    Text(
                        text = HOME_EMPTY_DESCRIPTION,
                        color = ChalkakTheme.colors.textMuted,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(HomeTopBarHeight))
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
                    HomeTopic(
                        dateLabel = uiState.dateLabel,
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
            HomeTopBar(
                modifier = Modifier
                    .then(
                        if (scrollState.isTopAreaVisible) {
                            Modifier.homeBottomDivider()
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
                onAction(HomeUiAction.BottomBarSelected(item))
            },
            onAddClick = { onAction(HomeUiAction.AddClicked) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { scrollState.bottomBarState.height = it.height }
                .graphicsLayer { translationY = scrollState.bottomBarState.offset },
        )
    }
}

private val HomeTopBarHeight = 55.dp

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun HomeScreenPreview() {
    ChalkakTheme {
        HomeScreen(
            uiState = HomeUiState(
                contentStatus = HomeContentStatus.Content,
                dateLabel = "8월 3일 · 오늘의 주제",
                topic = "하늘하늘하늘",
                photos = listOf(
                    Post(
                        id = "preview-1",
                        imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "노을이 진 하늘과 전신주",
                        title = "안녕하세요 찰캌입니다.",
                        likeCount = 24,
                    ),
                    Post(
                        id = "preview-2",
                        imageUrl = drawableResourceUrl(R.drawable.preview_photo),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "두 번째 사진",
                        title = null,
                        likeCount = 12,
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"

internal val HomeInitialError.message: String
    get() = when (this) {
        HomeInitialError.TopicNotFound -> "오늘의 주제가 아직 준비되지 않았어요"
        HomeInitialError.Unauthorized -> "로그인 정보를 확인할 수 없어요"
        HomeInitialError.Network -> "네트워크 연결을 확인해 주세요"
        HomeInitialError.InvalidResponse -> "홈 정보를 불러오지 못했어요"
        HomeInitialError.Client -> "요청을 처리하지 못했어요"
        HomeInitialError.Server -> "서버에 잠시 문제가 생겼어요"
        HomeInitialError.Generic -> HOME_ERROR_MESSAGE
    }
