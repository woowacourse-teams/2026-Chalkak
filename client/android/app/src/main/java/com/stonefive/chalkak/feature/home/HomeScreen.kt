package com.stonefive.chalkak.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.sort.ChalkakSortSelector
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.feature.home.component.HomePhotoList
import com.stonefive.chalkak.feature.home.component.HomeTopBar
import com.stonefive.chalkak.feature.home.component.HomeTopic
import com.stonefive.chalkak.feature.home.component.homeBottomDivider

@Composable
fun HomeRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
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

    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoListState = rememberLazyListState()
    var isTopAreaVisible by remember { mutableStateOf(true) }
    val topBarBackgroundAlpha by animateFloatAsState(
        targetValue = if (isTopAreaVisible) 1f else COLLAPSED_TOP_BAR_BACKGROUND_ALPHA,
        label = "home_top_bar_background_alpha",
    )
    val topBarBackgroundBrush = Brush.verticalGradient(
        colors = listOf(
            ChalkakBackground.copy(alpha = topBarBackgroundAlpha),
            ChalkakBackground.copy(alpha = topBarBackgroundAlpha * 0.72f),
            Color.Transparent,
        ),
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    when {
                        available.y < 0f -> isTopAreaVisible = false
                        available.y > 0f -> isTopAreaVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(uiState.selectedSort) {
        isTopAreaVisible = true
        photoListState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.TODAY,
                onItemSelected = { onAction(HomeUiAction.BottomBarSelected(it)) },
                onAddClick = { onAction(HomeUiAction.AddClicked) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .nestedScroll(nestedScrollConnection),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = isTopAreaVisible,
                    enter = slideInVertically(initialOffsetY = { -it }) + expandVertically(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(
                            modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars),
                        )
                        Spacer(modifier = Modifier.height(HomeTopBarHeight))
                        HomeTopic(
                            dateLabel = uiState.dateLabel,
                            topic = uiState.topic,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ChalkakSortSelector(
                            options = PostSort.entries,
                            selectedOption = uiState.selectedSort,
                            optionLabel = { it.label },
                            onOptionSelected = { onAction(HomeUiAction.SortSelected(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                HomePhotoList(
                    photos = uiState.photos,
                    likedPhotoIds = uiState.likedPhotoIds,
                    onLikeClick = { onAction(HomeUiAction.LikeClicked(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = photoListState,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(topBarBackgroundBrush),
            ) {
                HomeTopBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .then(
                            if (isTopAreaVisible) {
                                Modifier.homeBottomDivider()
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

private val HomeTopBarHeight = 55.dp
private const val COLLAPSED_TOP_BAR_BACKGROUND_ALPHA = 0.86f

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

private val PostSort.label: String
    get() = when (this) {
        PostSort.LATEST -> "최신순"
        PostSort.POPULAR -> "인기순"
        PostSort.RANDOM -> "랜덤순"
    }
