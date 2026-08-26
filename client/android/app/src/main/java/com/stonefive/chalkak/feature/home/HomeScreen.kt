package com.stonefive.chalkak.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
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

@Composable
fun HomeRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    selectionSignal: Int = 0,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                HomeUiEvent.OpenPhotoUpload -> onOpenPhotoUpload()
                is HomeUiEvent.NavigateToBottomBar -> onNavigateToBottomBar(event.item)
            }
        }
    }

    LaunchedEffect(selectionSignal) {
        if (selectionSignal > 0) {
            viewModel.onHomeSelected()
        }
    }

    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        resetSignal = selectionSignal,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0,
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

    LaunchedEffect(uiState.selectedSort) {
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
        HomePhotoList(
            photos = uiState.photos,
            likedPhotoIds = uiState.likedPhotoIds,
            onLikeClick = { onAction(HomeUiAction.LikeClicked(it)) },
            modifier = Modifier.fillMaxSize(),
            state = photoListState,
            topContentPadding = photoListTopPadding,
        )
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
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { localResetSignal++ },
                    ),
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
                isLoading = false,
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
            onAction = {},
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
