package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter

data class SharedPhotoSource(
    val modifier: Modifier,
    val onSuccess: (AsyncImagePainter.State.Success) -> Unit,
    val select: () -> SharedPhotoSnapshot?,
    val signatureModifier: Modifier,
    val onSignatureSuccess: (AsyncImagePainter.State.Success) -> Unit,
)

@Composable
fun rememberSharedPhotoSource(
    key: PhotoTransitionKey,
    imageModel: Any?,
    signatureModel: Any?,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.FillWidth,
    sourceShape: CornerBasedShape? = null,
): SharedPhotoSource {
    val coordinator = LocalPhotoTransitionCoordinator.current
    val sourceModifier = Modifier.sharedPhotoElement(key, sourceShape)
    val signatureModifier = Modifier.sharedPhotoElement(key.signatureKey(), animateCorners = false)
    var loadedSignature by remember(key, signatureModel) {
        mutableStateOf<AsyncImagePainter.State.Success?>(null)
    }

    LaunchedEffect(coordinator, key, imageModel, signatureModel, aspectRatio, contentScale, sourceShape) {
        coordinator.register(
            SharedPhotoSnapshot(
                key = key,
                imageModel = imageModel,
                signatureModel = loadedSignature
                    ?.result
                    ?.request
                    ?.data ?: signatureModel,
                signaturePainter = loadedSignature?.painter,
                painter = null,
                image = null,
                memoryCacheKey = null,
                aspectRatio = aspectRatio,
                contentScale = contentScale,
                sourceShape = sourceShape,
            ),
        )
    }

    DisposableEffect(coordinator, key) {
        onDispose { coordinator.unregister(key) }
    }

    return remember(key, imageModel, signatureModel, sourceModifier, coordinator, contentScale, sourceShape) {
        SharedPhotoSource(
            modifier = sourceModifier,
            onSuccess = { state ->
                coordinator.register(
                    SharedPhotoSnapshot(
                        key = key,
                        imageModel = state.result.request.data,
                        signatureModel = loadedSignature
                            ?.result
                            ?.request
                            ?.data ?: signatureModel,
                        signaturePainter = loadedSignature?.painter,
                        painter = state.painter,
                        image = state.result.image,
                        memoryCacheKey = state.result.memoryCacheKey,
                        aspectRatio = aspectRatioForSize(
                            width = state.result.image.width,
                            height = state.result.image.height,
                        ) ?: aspectRatio,
                        contentScale = contentScale,
                        sourceShape = sourceShape,
                    ),
                )
            },
            select = { coordinator.select(key) },
            signatureModifier = signatureModifier,
            onSignatureSuccess = { state ->
                loadedSignature = state
                coordinator.registerSignature(key, state.result.request.data, state.painter)
            },
        )
    }
}

fun aspectRatioForSize(
    width: Int,
    height: Int,
): Float? = if (width > 0 && height > 0) {
    width.toFloat() / height
} else {
    null
}
