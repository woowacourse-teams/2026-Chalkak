package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun DisplayPhotoCard(
    photo: Post,
    modifier: Modifier = Modifier,
    variant: DisplayPhotoCardVariant = DisplayPhotoCardVariant.GRID,
    onClick: (() -> Unit)? = null,
) {
    val isFeatured = variant == DisplayPhotoCardVariant.FEATURED
    val photoClickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = photo.contentDescription
            }.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
    }

    Box(
        modifier = modifier
            .clip(ChalkakTheme.shapes.photoCard)
            .background(Color.Black)
            .then(photoClickModifier),
    ) {
        ChalkakSignedImage(
            imageModel = photo.imageUrl,
            signatureModel = photo.signatureUrl,
            contentDescription = if (onClick == null) photo.contentDescription else null,
            contentScale = if (isFeatured) ContentScale.Fit else ContentScale.FillWidth,
            signatureModifier = Modifier.size(
                width = if (isFeatured) 48.dp else 40.dp,
                height = if (isFeatured) 36.dp else 30.dp,
            ),
        )

        DisplayLikeCount(
            likeCount = photo.likeCount,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        )

        if (isFeatured) {
            Text(
                text = photo.title.orEmpty(),
                color = ChalkakTheme.colors.textOnImage,
                style = ChalkakTheme.typography.handwriting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

enum class DisplayPhotoCardVariant {
    GRID,
    FEATURED,
}

@Composable
fun DisplayLikeCount(
    likeCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "좋아요 $likeCount"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_heart),
            contentDescription = null,
            tint = ChalkakTheme.colors.textOnImage,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = likeCount.toString(),
            color = ChalkakTheme.colors.textOnImage,
            style = ChalkakTheme.typography.subheadline.copy(
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 180)
@Composable
private fun DisplayPhotoCardPreview() {
    ChalkakTheme {
        DisplayPhotoCard(
            photo = previewDisplayPost,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisplayLikeCountPreview() {
    ChalkakTheme {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .padding(12.dp),
        ) {
            DisplayLikeCount(likeCount = 17)
        }
    }
}
