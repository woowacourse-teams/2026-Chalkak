package com.stonefive.chalkak.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    postId: String? = null,
    initialContent: FeedContentState.Success? = null,
    isOwnedByCurrentUser: Boolean = initialContent?.post?.isOwnedByCurrentUser == true,
    viewModel: FeedViewModel = viewModel(
        key = "feed-${postId ?: initialContent?.post?.id ?: "latest"}",
        factory = FeedViewModel.factory(
            postId = postId,
            initialContent = initialContent,
            isOwnedByCurrentUser = isOwnedByCurrentUser,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FeedScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDeleteClick = onDeleteClick,
        onLikeClick = viewModel::onLikeClicked,
        onRetryClick = viewModel::retry,
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
    onRetryClick: () -> Unit = {},
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
                        start = 8.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 8.dp,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.content != null -> FeedContent(
                    content = checkNotNull(uiState.content),
                    onLikeClick = onLikeClick,
                    modifier = Modifier.fillMaxSize(),
                )

                uiState.isLoading -> CircularProgressIndicator(
                    color = ChalkakTheme.colors.actionPrimary,
                )

                uiState.errorMessage != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.body,
                    )
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xl))
                    androidx.compose.material3.TextButton(onClick = onRetryClick) {
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

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedScreenPreview() {
    val resourcePrefix = "android.resource://com.stonefive.chalkak"

    ChalkakTheme {
        FeedScreen(
            uiState = FeedUiState(
                content = FeedContentState.Success(
                    dateLabel = "8월 3일의 주제",
                    topic = "하늘하늘하늘",
                    post = Post(
                        id = "preview",
                        originalImageUrl = "$resourcePrefix/${R.drawable.home_feed_photo}",
                        thumbnailImageUrl = "$resourcePrefix/${R.drawable.home_feed_photo}",
                        signatureOriginalImageUrl = "$resourcePrefix/${R.drawable.preview_signature}",
                        signatureThumbnailImageUrl = "$resourcePrefix/${R.drawable.preview_signature}",
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
