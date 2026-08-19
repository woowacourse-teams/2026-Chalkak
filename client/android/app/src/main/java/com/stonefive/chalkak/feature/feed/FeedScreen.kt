package com.stonefive.chalkak.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

private val FeedHorizontalPadding = 20.dp
private val FeedPhotoAspectRatio = 0.935f
private val FeedDivider = Color(0xFFB8B5AF)

@Composable
fun FeedRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.Factory),
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
        FeedBody(
            content = uiState.content,
            onLikeClick = onLikeClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun FeedTopBar(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_display_arrow_left),
                contentDescription = "뒤로 가기",
                tint = ChalkakTheme.colors.iconPrimary,
            )
        }
        Text(
            text = "피드",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2,
        )
    }
}

@Composable
private fun FeedBody(
    content: FeedContentState,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        FeedContentState.Loading -> FeedLoadingContent(modifier = modifier)

        is FeedContentState.Success -> FeedPostContent(
            content = content,
            onLikeClick = onLikeClick,
            modifier = modifier,
        )

        is FeedContentState.Error -> FeedErrorContent(
            message = content.message,
            modifier = modifier,
        )
    }
}

@Composable
private fun FeedPostContent(
    content: FeedContentState.Success,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item(key = "topic") {
            FeedTopic(
                dateLabel = content.dateLabel,
                topic = content.topic,
            )
        }
        item(key = content.post.id) {
            FeedPhoto(
                post = content.post,
                isLiked = content.isLiked,
                onLikeClick = onLikeClick,
            )
        }
        item(key = "caption") {
            FeedCaption(title = content.post.title)
        }
    }
}

@Composable
private fun FeedTopic(
    dateLabel: String,
    topic: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FeedHorizontalPadding,
                top = 25.dp,
                end = FeedHorizontalPadding,
                bottom = 40.dp,
            ),
    ) {
        Text(
            text = dateLabel,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
        )
        Text(
            text = topic,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.display,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
private fun FeedPhoto(
    post: Post,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ChalkakSignedImage(
            imageModel = post.imageUrl,
            signatureModel = post.signatureUrl,
            contentDescription = post.contentDescription,
            contentScale = ContentScale.Crop,
            signatureModifier = Modifier.size(
                width = 70.dp,
                height = 52.dp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FeedPhotoAspectRatio),
        )
        FeedLikeRow(
            likeCount = post.likeCount,
            isLiked = isLiked,
            onLikeClick = onLikeClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeedLikeRow(
    likeCount: Int,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(70.dp)
            .padding(horizontal = FeedHorizontalPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = "좋아요 $likeCount"
                stateDescription = if (isLiked) "좋아요 선택됨" else "좋아요 선택 안 됨"
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onLikeClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
            ),
            contentDescription = null,
            tint = if (isLiked) {
                ChalkakTheme.colors.actionPrimary
            } else {
                ChalkakTheme.colors.iconSecondary
            },
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = likeCount.toString(),
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body
                .copy(fontWeight = FontWeight.Normal),
        )
    }
}

@Composable
private fun FeedCaption(
    title: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .feedTopDivider()
            .padding(
                start = FeedHorizontalPadding,
                top = 16.dp,
                end = FeedHorizontalPadding,
            ),
    ) {
        Text(
            text = title?.takeIf { it.isNotBlank() } ?: "무제",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.feedTopDivider(): Modifier = drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    drawLine(
        color = FeedDivider,
        start = Offset(0f, strokeWidth / 2),
        end = Offset(size.width, strokeWidth / 2),
        strokeWidth = strokeWidth,
    )
}

@Composable
private fun FeedLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        CircularProgressIndicator(
            color = ChalkakTheme.colors.actionPrimary,
            modifier = Modifier.padding(top = 64.dp),
        )
    }
}

@Composable
private fun FeedErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
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
