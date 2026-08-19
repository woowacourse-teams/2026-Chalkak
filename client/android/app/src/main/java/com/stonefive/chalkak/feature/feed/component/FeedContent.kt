package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.FeedContentState

@Composable
fun FeedContent(
    content: FeedContentState.Success?,
    onLikeClick: () -> Unit,
    captionModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    FeedPostContent(
        content = content,
        onLikeClick = onLikeClick,
        captionModifier = captionModifier,
        modifier = modifier,
    )
}

@Preview(name = "성공", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedContentPreview() {
    ChalkakTheme {
        FeedContent(
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
            onLikeClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FeedPostContent(
    content: FeedContentState.Success,
    onLikeClick: () -> Unit,
    captionModifier: Modifier = Modifier,
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
                modifier = Modifier.fillMaxWidth(),
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
            FeedCaption(
                title = content.post.title,
                modifier = captionModifier,
            )
        }
    }
}
