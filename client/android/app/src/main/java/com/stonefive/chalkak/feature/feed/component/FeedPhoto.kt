package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

private val FeedPhotoAspectRatio = 0.935f

@Composable
fun FeedPhoto(
    post: Post,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedPhotoPreview() {
    ChalkakTheme {
        FeedPhoto(
            post = Post(
                id = "preview",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "노을이 진 하늘과 전신주",
                title = "안녕하세요 감사합니다.",
                likeCount = 24,
            ),
            isLiked = false,
            onLikeClick = {},
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
            .height(60.dp)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp)
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
