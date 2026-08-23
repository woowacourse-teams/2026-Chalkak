package com.stonefive.chalkak.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
        onLikeClick = viewModel::onLikeClicked,
        modifier = modifier,
    )
}

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onNavigateBack: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground)
            .statusBarsPadding(),
    ) {
        FeedTopBar(
            onNavigateBack = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedContent(
            content = uiState.content,
            onLikeClick = onLikeClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    ),
                    isLiked = false,
                ),
            ),
            onNavigateBack = {},
            onLikeClick = {},
        )
    }
}
