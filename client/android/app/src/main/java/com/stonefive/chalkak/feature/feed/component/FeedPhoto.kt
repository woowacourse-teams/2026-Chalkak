package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.LocalSharedTransitionScope
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.SharedPhotoImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post

@Composable
fun FeedPhoto(
    post: Post?,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    entryPostId: String? = null,
    entryThumbnailImageUrl: String? = null,
) {
    val postId = post?.id ?: entryPostId ?: return
    val entrySnapshot = LocalPhotoTransitionCoordinator.current.selectedSnapshot(postId)
    Column(modifier = modifier) {
        ZoomableFeedPhotoSurface(
            modifier = Modifier.fillMaxWidth(),
            resetKey = postId,
        ) {
            if (entrySnapshot != null || entryThumbnailImageUrl != null) {
                SharedPhotoImage(
                    key = entrySnapshot?.key ?: PhotoTransitionKey(postId, "record:$postId"),
                    originalImageModel = post?.originalImageUrl,
                    thumbnailImageModel = post?.thumbnailImageUrl ?: entryThumbnailImageUrl,
                    signatureModel = post?.signatureOriginalImageUrl,
                    thumbnailSignatureModel = post?.signatureThumbnailImageUrl,
                    contentDescription = post?.contentDescription ?: "기록 사진",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (post != null) {
                ChalkakSignedImage(
                    imageModel = post.originalImageUrl,
                    signatureModel = post.signatureOriginalImageUrl,
                    contentDescription = post.contentDescription,
                    thumbnailImageModel = post.thumbnailImageUrl,
                    thumbnailSignatureModel = post.signatureThumbnailImageUrl,
                    contentScale = ContentScale.FillWidth,
                    signatureModifier = Modifier.size(
                        width = 70.dp,
                        height = 52.dp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (post != null) {
            FeedLikeRow(
                likeCount = post.likeCount,
                isLiked = isLiked,
                onLikeClick = onLikeClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ZoomableFeedPhotoSurface(
    resetKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(MIN_FEED_PHOTO_SCALE) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var size by remember(resetKey) { mutableStateOf(IntSize.Zero) }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val overlayModifier = if (sharedTransitionScope != null) {
        with(sharedTransitionScope) {
            // 손가락으로 사진을 조작하는 동안 스크롤 영역 밖에도 그린다.
            Modifier.renderInSharedTransitionScopeOverlay(renderInOverlay = {
                scale > MIN_FEED_PHOTO_SCALE || offset != Offset.Zero
            })
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .testTag(FEED_PHOTO_ZOOM_SURFACE_TAG)
            .then(overlayModifier)
            .then(if (scale > MIN_FEED_PHOTO_SCALE || offset != Offset.Zero) Modifier else Modifier.clipToBounds())
            .onSizeChanged { size = it }
            .pointerInput(resetKey) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= FEED_PHOTO_ZOOM_POINTER_COUNT) {
                            val nextScale = feedPhotoZoomScale(scale, event.calculateZoom())
                            offset = feedPhotoZoomOffset(
                                offset = feedPhotoPinchOffset(
                                    offset = offset,
                                    previousScale = scale,
                                    nextScale = nextScale,
                                    previousCentroid = event.calculateCentroid(useCurrent = false),
                                    currentCentroid = event.calculateCentroid(),
                                    size = size,
                                ),
                                scale = nextScale,
                                size = size,
                            )
                            scale = nextScale
                            event.changes
                                .filter { it.positionChanged() }
                                .forEach { it.consume() }
                        } else if (pressedCount == 1 &&
                            (scale > MIN_FEED_PHOTO_SCALE || offset != Offset.Zero)
                        ) {
                            offset = feedPhotoZoomOffset(
                                offset = offset + event.calculatePan(),
                                scale = scale,
                                size = size,
                            )
                            event.changes
                                .filter { it.positionChanged() }
                                .forEach { it.consume() }
                        } else if (pressedCount == 0) {
                            scale = MIN_FEED_PHOTO_SCALE
                            offset = Offset.Zero
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            content()
        }
    }
}

internal fun feedPhotoZoomScale(
    currentScale: Float,
    zoomChange: Float,
): Float = (currentScale * zoomChange)
    .coerceIn(MIN_FEED_PHOTO_SCALE, MAX_FEED_PHOTO_SCALE)

internal fun feedPhotoPinchOffset(
    offset: Offset,
    previousScale: Float,
    nextScale: Float,
    previousCentroid: Offset,
    currentCentroid: Offset,
    size: IntSize,
): Offset {
    if (!previousCentroid.isSpecified || !currentCentroid.isSpecified) return offset

    val center = Offset(size.width / 2f, size.height / 2f)
    val scaleChange = nextScale / previousScale
    return currentCentroid - center - (previousCentroid - center - offset) * scaleChange
}

internal fun feedPhotoZoomOffset(
    offset: Offset,
    scale: Float,
    size: IntSize,
): Offset {
    if (size.width == 0 || size.height == 0) return Offset.Zero

    val maxX = size.width * scale / 2f
    val maxY = size.height * scale / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedPhotoPreview() {
    ChalkakTheme {
        FeedPhoto(
            post = Post(
                id = "preview",
                originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                signatureThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "노을이 진 하늘과 전신주",
                title = "안녕하세요 감사합니다.",
                likeCount = 24,
            ),
            isLiked = false,
            onLikeClick = {},
        )
    }
}

private const val FEED_PHOTO_ZOOM_POINTER_COUNT = 2
private const val MIN_FEED_PHOTO_SCALE = 1f
private const val MAX_FEED_PHOTO_SCALE = 4f
internal const val FEED_PHOTO_ZOOM_SURFACE_TAG = "feed_photo_zoom_surface"

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
