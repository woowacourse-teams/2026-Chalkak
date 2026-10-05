package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SharedPhotoImage(
    key: PhotoTransitionKey,
    originalImageModel: Any?,
    thumbnailImageModel: Any?,
    signatureModel: Any?,
    thumbnailSignatureModel: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    fallbackAspectRatio: Float? = null,
    signatureModifier: Modifier = Modifier.size(
        width = 70.dp,
        height = 52.dp,
    ),
) {
    val coordinator = LocalPhotoTransitionCoordinator.current
    val selectedSnapshot = coordinator.selectedSnapshot(key.postId)
    val sourceImageModel = selectedSnapshot?.imageModel ?: thumbnailImageModel
    val sourceSignatureModel = selectedSnapshot?.signatureModel ?: thumbnailSignatureModel
    var loadedAspectRatio by remember(key, thumbnailImageModel) { mutableStateOf<Float?>(null) }
    val aspectRatio = selectedSnapshot?.aspectRatio
        ?: fallbackAspectRatio
        ?: loadedAspectRatio
        ?: DEFAULT_PHOTO_ASPECT_RATIO
    val sourceContentScale = selectedSnapshot?.contentScale ?: contentScale
    val revealState = rememberSharedPhotoImageRevealState(originalImageModel)
    val originalAlpha by animateFloatAsState(
        targetValue = if (revealState.shouldShowOriginal) 1f else 0f,
        animationSpec = tween(durationMillis = PHOTO_CROSSFADE_DURATION_MILLIS),
        label = "shared-photo-alpha",
    )
    val resolvedModifier = modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)

    Box(modifier = resolvedModifier) {
        Box(
            modifier = Modifier
                .sharedPhotoElement(key)
                .fillMaxSize(),
        ) {
            ChalkakImage(
                model = sourceImageModel,
                contentDescription = contentDescription,
                contentScale = sourceContentScale,
                loadedPainter = selectedSnapshot?.painter,
                onSuccess = { state ->
                    if (selectedSnapshot?.aspectRatio == null) {
                        loadedAspectRatio = aspectRatioForSize(
                            width = state.result.image.width,
                            height = state.result.image.height,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (originalImageModel != null) {
                ChalkakImage(
                    model = originalImageModel,
                    contentDescription = null,
                    thumbnailModel = sourceImageModel,
                    contentScale = contentScale,
                    onSuccess = { revealState.onOriginalLoadSuccess() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = originalAlpha },
                )
            }
        }

        if (sourceSignatureModel != null || signatureModel != null) {
            var originalSignatureLoaded by remember(signatureModel) { mutableStateOf(false) }
            val signatureAlpha by animateFloatAsState(
                targetValue = if (originalSignatureLoaded && revealState.shouldShowOriginal) 1f else 0f,
                animationSpec = tween(PHOTO_CROSSFADE_DURATION_MILLIS),
                label = "shared-photo-signature-alpha",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .sharedPhotoElement(key.signatureKey(), animateCorners = false)
                    .then(signatureModifier),
            ) {
                if (sourceSignatureModel != null) {
                    ChalkakImage(
                        model = sourceSignatureModel,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        loadedPainter = selectedSnapshot?.signaturePainter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (signatureModel != null) {
                    ChalkakImage(
                        model = signatureModel,
                        contentDescription = null,
                        thumbnailModel = sourceSignatureModel,
                        contentScale = ContentScale.Fit,
                        onSuccess = { originalSignatureLoaded = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = signatureAlpha },
                    )
                }
            }
        }
    }
}

private const val DEFAULT_PHOTO_ASPECT_RATIO = 1f
private const val PHOTO_CROSSFADE_DURATION_MILLIS = 180

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun SharedPhotoImagePreview() {
    ChalkakTheme {
        SharedPhotoImage(
            key = PhotoTransitionKey("preview", "preview"),
            originalImageModel = R.drawable.home_feed_photo,
            thumbnailImageModel = R.drawable.home_feed_photo,
            signatureModel = R.drawable.preview_signature,
            thumbnailSignatureModel = null,
            contentDescription = "사진",
            fallbackAspectRatio = 0.75f,
        )
    }
}
