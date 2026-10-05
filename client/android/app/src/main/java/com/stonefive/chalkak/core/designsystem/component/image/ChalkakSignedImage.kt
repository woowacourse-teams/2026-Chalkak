package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakSignedImage(
    imageModel: Any?,
    signatureModel: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbnailImageModel: Any? = null,
    thumbnailSignatureModel: Any? = null,
    imageModifier: Modifier = Modifier,
    signatureModifier: Modifier = Modifier.size(
        width = 56.dp,
        height = 42.dp,
    ),
    contentScale: ContentScale = ContentScale.Crop,
    loadedImagePainter: Painter? = null,
    loadedSignaturePainter: Painter? = null,
    onImageSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onThumbnailImageSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onSignatureSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onThumbnailSignatureSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    imageAspectRatio: Float? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ChalkakImage(
            model = imageModel,
            contentDescription = contentDescription,
            thumbnailModel = thumbnailImageModel,
            modifier = Modifier
                .then(imageModifier)
                .then(
                    if (imageAspectRatio != null) {
                        Modifier.aspectRatio(imageAspectRatio)
                    } else {
                        Modifier.fillMaxSize()
                    },
                ),
            contentScale = contentScale,
            loadedPainter = loadedImagePainter,
            onSuccess = onImageSuccess,
            onThumbnailSuccess = onThumbnailImageSuccess,
        )

        ChalkakImage(
            model = signatureModel,
            contentDescription = null,
            thumbnailModel = thumbnailSignatureModel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .then(signatureModifier),
            contentScale = ContentScale.Fit,
            loadedPainter = loadedSignaturePainter,
            onSuccess = onSignatureSuccess,
            onThumbnailSuccess = onThumbnailSignatureSuccess,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakSignedImagePreview() {
    ChalkakTheme {
        ChalkakSignedImage(
            imageModel = R.drawable.preview_photo,
            signatureModel = R.drawable.preview_signature,
            contentDescription = null,
            modifier = Modifier.size(
                width = 270.dp,
                height = 360.dp,
            ),
        )
    }
}
