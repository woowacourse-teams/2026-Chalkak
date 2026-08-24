package com.stonefive.chalkak.feature.feed

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.component.FeedContent
import com.stonefive.chalkak.feature.feed.component.FeedTopBar

@Composable
fun FeedRoute(
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    initialContent: FeedContentState.Success? = null,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(
        key = "feed-${initialContent?.post?.id ?: "latest"}",
        factory = FeedViewModel.factory(initialContent),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FeedScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDeleteClick = onDeleteClick,
        onLikeClick = viewModel::onLikeClicked,
        modifier = modifier,
    )
}

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FeedTopBar(
                onNavigateBack = onNavigateBack,
                onDeleteClick = onDeleteClick,
                isDeleteVisible = uiState.content
                    ?.post
                    ?.isOwnedByCurrentUser == true,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = 4.dp,
                        end = 12.dp,
                        bottom = 8.dp,
                    ),
            )
        },
    ) { innerPadding ->
        FeedContent(
            content = uiState.content,
            onLikeClick = onLikeClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedScreenPreview() {
    ChalkakTheme {
        FeedScreen(
            uiState = FeedUiState(
                content = FeedContentState.Success(
                    dateLabel = "8월 3일의 주제",
                    topic = "하늘하늘하늘",
                    post = Post(
                        id = "preview",
                        imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                        signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                        contentDescription = "노을이 진 하늘과 전신주",
                        title = "안녕하세요 감사합니다.",
                        likeCount = 24,
                        isOwnedByCurrentUser = true,
                    ),
                    isLiked = false,
                ),
            ),
            onNavigateBack = {},
            onDeleteClick = {},
            onLikeClick = {},
        )
    }
}
