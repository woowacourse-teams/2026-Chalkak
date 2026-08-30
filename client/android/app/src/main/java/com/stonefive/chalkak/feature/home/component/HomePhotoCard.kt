package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite
import com.stonefive.chalkak.domain.model.Post

private val HomeText = Color(0xFF7D7D7D)

@Composable
fun HomePhotoCard(
    photo: Post,
    isLiked: Boolean,
    isLikeEnabled: Boolean,
    onLikeClick: () -> Unit,
    onPhotoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RectangleShape,
                clip = false,
            ).background(ChalkakWhite),
    ) {
        ChalkakSignedImage(
            imageModel = photo.originalImageUrl,
            signatureModel = photo.signatureOriginalImageUrl,
            contentDescription = photo.contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(415.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = photo.contentDescription
                }.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onPhotoClick,
                ),
        )
        PhotoActionRow(
            photo = photo,
            isLiked = isLiked,
            isLikeEnabled = isLikeEnabled,
            onLikeClick = onLikeClick,
        )
    }
}

@Composable
private fun PhotoActionRow(
    photo: Post,
    isLiked: Boolean,
    isLikeEnabled: Boolean,
    onLikeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(57.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .semantics { contentDescription = "좋아요 ${photo.likeCount}" }
                .clickable(
                    enabled = isLikeEnabled,
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
                tint = if (isLiked) ChalkakTheme.colors.actionPrimary else HomeText,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = photo.likeCount.toString(),
                color = HomeText,
                style = ChalkakTheme.typography.body
                    .copy(fontWeight = FontWeight.Normal),
            )
        }
        Text(
            text = photo.title?.takeIf { it.isNotBlank() } ?: "무제",
            color = HomeText,
            style = ChalkakTheme.typography.body
                .copy(fontWeight = FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomePhotoCardPreview() {
    ChalkakTheme {
        HomePhotoCard(
            photo = Post(
                id = "preview",
                originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                contentDescription = "노을이 진 하늘과 전신주",
                title = "안녕하세요 찰캌입니다.",
                likeCount = 24,
            ),
            isLiked = true,
            isLikeEnabled = true,
            onLikeClick = {},
            onPhotoClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
