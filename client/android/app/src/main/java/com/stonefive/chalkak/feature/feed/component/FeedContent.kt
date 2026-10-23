package com.stonefive.chalkak.feature.feed.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.FeedContentState
import java.time.LocalDate

private const val FEED_TOPIC_RESIZE_DURATION_MILLIS = 180

@Composable
fun FeedContent(
    content: FeedContentState.Success?,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    entryPostId: String? = null,
    entryThumbnailImageUrl: String? = null,
    onPhotoBoundsChanged: (Rect) -> Unit = {},
) {
    if (content == null && (entryPostId == null || entryThumbnailImageUrl == null)) return

    FeedPostContent(
        content = content,
        onLikeClick = onLikeClick,
        modifier = modifier,
        entryPostId = entryPostId,
        entryThumbnailImageUrl = entryThumbnailImageUrl,
        onPhotoBoundsChanged = onPhotoBoundsChanged,
    )
}

@Preview(name = "성공", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun FeedContentPreview() {
    val resourcePrefix = "android.resource://com.stonefive.chalkak"

    ChalkakTheme {
        FeedContent(
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
                ),
                isLiked = false,
                topicDate = LocalDate
                    .of(2026, 8, 3),
            ),
            onLikeClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FeedPostContent(
    content: FeedContentState.Success?,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    entryPostId: String? = null,
    entryThumbnailImageUrl: String? = null,
    onPhotoBoundsChanged: (Rect) -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        FeedTopic(
            dateLabel = content?.dateLabel.orEmpty(),
            topic = content?.topic.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(FEED_TOPIC_RESIZE_DURATION_MILLIS)),
        )
        FeedPhoto(
            post = content?.post,
            isLiked = content?.isLiked == true,
            onLikeClick = onLikeClick,
            modifier = Modifier.fillMaxWidth(),
            entryPostId = entryPostId,
            entryThumbnailImageUrl = entryThumbnailImageUrl,
            onPhotoBoundsChanged = onPhotoBoundsChanged,
        )
    }
}
