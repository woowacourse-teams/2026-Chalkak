package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feed.FeedContentState

@Composable
fun FeedContent(
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
