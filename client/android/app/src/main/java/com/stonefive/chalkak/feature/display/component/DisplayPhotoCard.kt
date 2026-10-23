package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.component.image.FittedPhotoCardShape
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.rememberSharedPhotoSource
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun DisplayPhotoCard(
    photo: Post,
    modifier: Modifier = Modifier,
    variant: DisplayPhotoCardVariant = DisplayPhotoCardVariant.GRID,
    onClick: (() -> Unit)? = null,
    onLikeClick: (() -> Unit)? = null,
    imageAspectRatio: Float? = null,
    onImageAspectRatioAvailable: (Float) -> Unit = {},
    transitionSourceId: String = "display-${variant.name}:${photo.id}",
) {
    val isFeatured = variant == DisplayPhotoCardVariant.FEATURED
    var loadedImageAspectRatio by rememberSaveable(photo.originalImageUrl, photo.thumbnailImageUrl) {
        mutableStateOf<Float?>(null)
    }
    val photoSource = rememberSharedPhotoSource(
        key = PhotoTransitionKey(photo.id, transitionSourceId),
        imageModel = if (isFeatured) photo.originalImageUrl else photo.thumbnailImageUrl,
        signatureModel = if (isFeatured) photo.signatureOriginalImageUrl else photo.signatureThumbnailImageUrl,
        aspectRatio = imageAspectRatio,
        contentScale = if (isFeatured) ContentScale.Fit else ContentScale.FillWidth,
        sourceShape = if (isFeatured) {
            FittedPhotoCardShape(ChalkakTheme.shapes.photoCard, FEATURED_PHOTO_ASPECT_RATIO)
        } else {
            ChalkakTheme.shapes.photoCard
        },
    )
    val context = LocalPlatformContext.current
    val currentOnImageAspectRatioAvailable by rememberUpdatedState(onImageAspectRatioAvailable)
    val thumbnailImageRequest = remember(context, photo.thumbnailImageUrl) {
        ImageRequest
            .Builder(context)
            .data(photo.thumbnailImageUrl)
            .listener(
                onSuccess = { _, result ->
                    aspectRatioForSize(result.image.width, result.image.height)
                        ?.let(currentOnImageAspectRatioAvailable)
                },
            ).build()
    }
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
                onClick = {
                    photoSource.select()
                    onClick()
                },
            )
    }

    Box(
        modifier = modifier
            .clip(ChalkakTheme.shapes.photoCard)
            .background(Color.Black)
            .then(photoClickModifier),
    ) {
        ChalkakSignedImage(
            onImageSuccess = { state ->
                photoSource.onSuccess(state)
                loadedImageAspectRatio = aspectRatioForSize(state.result.image.width, state.result.image.height)
            },
            onThumbnailImageSuccess = { state ->
                photoSource.onSuccess(state)
                loadedImageAspectRatio = aspectRatioForSize(state.result.image.width, state.result.image.height)
            },
            onSignatureSuccess = photoSource.onSignatureSuccess,
            onThumbnailSignatureSuccess = photoSource.onSignatureSuccess,
            imageModifier = photoSource.modifier,
            imageModel = if (isFeatured) photo.originalImageUrl else thumbnailImageRequest,
            signatureModel = if (isFeatured) {
                photo.signatureOriginalImageUrl
            } else {
                photo.signatureThumbnailImageUrl
            },
            contentDescription = if (onClick == null) photo.contentDescription else null,
            thumbnailImageModel = photo.thumbnailImageUrl.takeIf { isFeatured },
            thumbnailSignatureModel = photo.signatureThumbnailImageUrl.takeIf { isFeatured },
            contentScale = if (isFeatured) ContentScale.Fit else ContentScale.FillWidth,
            imageAspectRatio = if (isFeatured) loadedImageAspectRatio ?: imageAspectRatio else null,
            modifier = if (isFeatured) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio ?: DEFAULT_GRID_IMAGE_ASPECT_RATIO)
            },
            signatureModifier = photoSource.signatureModifier.size(
                width = if (isFeatured) 48.dp else 40.dp,
                height = if (isFeatured) 36.dp else 30.dp,
            ),
        )

        DisplayLikeCount(
            likeCount = photo.likeCount,
            isLiked = photo.isLiked,
            onClick = onLikeClick,
            modifier = Modifier.align(Alignment.BottomStart),
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

private fun aspectRatioForSize(
    width: Int,
    height: Int,
): Float? = if (width > 0 && height > 0) {
    width.toFloat() / height
} else {
    null
}

/**
 * 사진 카드 좌하단에 올리는 좋아요 아이콘과 개수.
 *
 * 카드 모서리와 하트 사이 여백([LikeCardInset])까지 클릭 영역에 포함해서,
 * 하트 바깥 모서리 쪽을 눌러도 사진 클릭(피드 진입)이 아닌 좋아요로 처리한다.
 * 위·오른쪽으로는 [LikeTouchExpansion]만큼 더 넓혀 손가락이 하트를 조금 벗어나도 눌리게 한다.
 * 그래서 호출부는 바깥 여백 없이 카드의 BottomStart에 정렬만 하면 된다.
 * 예) 하트 18dp + 숫자 한 자리일 때 클릭 영역은 약 54×42dp이고, 하트는 카드 모서리에서 10dp 안쪽에 그려진다.
 */
@Composable
fun DisplayLikeCount(
    likeCount: Int,
    isLiked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Row(
        modifier = modifier
            .semantics {
                contentDescription = "좋아요 $likeCount"
                stateDescription = if (isLiked) "좋아요 선택됨" else "좋아요 선택 안 됨"
            }.then(clickModifier)
            .padding(
                start = LikeCardInset,
                top = LikeTouchExpansion,
                end = LikeTouchExpansion,
                bottom = LikeCardInset,
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
            ),
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

private const val DEFAULT_GRID_IMAGE_ASPECT_RATIO = 1f

// 카드 좌하단 모서리에서 하트까지의 거리. 이 여백도 좋아요 클릭 영역에 포함된다.
private val LikeCardInset = 10.dp

// 하트·숫자 위쪽과 오른쪽으로 클릭 영역을 더 넓히는 폭.
private val LikeTouchExpansion = 14.dp

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

internal const val FEATURED_PHOTO_ASPECT_RATIO = 3f / 4f
