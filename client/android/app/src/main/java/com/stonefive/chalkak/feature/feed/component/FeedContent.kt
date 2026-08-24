package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.FeedContentState

private val FeedCaptionHorizontalPadding = 20.dp

@Composable
fun FeedContent(
    content: FeedContentState.Success?,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    FeedPostContent(
        content = content,
        onLikeClick = onLikeClick,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        FeedTopic(
            dateLabel = content.dateLabel,
            topic = content.topic,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedPhoto(
            post = content.post,
            isLiked = content.isLiked,
            onLikeClick = onLikeClick,
            modifier = Modifier.fillMaxWidth(),
        )
        FeedCaption(
            title = content.post.title,
            modifier = Modifier
                .fillMaxWidth()
                .feedCaptionDivider(ChalkakTheme.colors.divider)
                .padding(horizontal = FeedCaptionHorizontalPadding, vertical = 5.dp),
        )
    }
}

private fun Modifier.feedCaptionDivider(color: Color): Modifier = drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, strokeWidth / 2),
        end = Offset(size.width, strokeWidth / 2),
        strokeWidth = strokeWidth,
    )
}
