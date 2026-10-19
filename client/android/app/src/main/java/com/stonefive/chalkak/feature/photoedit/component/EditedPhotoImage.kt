package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.feature.photoedit.CropGeometry
import com.stonefive.chalkak.feature.photoedit.PhotoImageSize
import com.stonefive.chalkak.feature.photoedit.rememberAdjustedImage
import com.stonefive.chalkak.feature.photoedit.toColorFilter

/** 원본 비율을 알아야 하므로 [imageSize]가 없으면 한 번 불러와 크기를 잰다. */
@Composable
fun EditedPhotoImage(
    model: Any?,
    edit: PhotoEdit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageSize: PhotoImageSize? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val colorFilter = remember(edit.filter) { edit.filter.toColorFilter() }
    val adjustedImage = rememberAdjustedImage(model, edit.adjustments)
    val adjustedPainter = remember(adjustedImage) { adjustedImage?.let(::BitmapPainter) }
    if (edit.isGeometryIdentity) {
        ChalkakImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            loadedPainter = adjustedPainter,
        )
        return
    }

    var measuredSize by remember(model) { mutableStateOf<PhotoImageSize?>(null) }
    val size = imageSize ?: measuredSize
    if (size == null) {
        ChalkakImage(
            model = model,
            contentDescription = null,
            modifier = modifier.alpha(0f),
            contentScale = contentScale,
            onSuccess = { state ->
                val intrinsicSize = state.painter.intrinsicSize
                if (intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
                    measuredSize = PhotoImageSize(intrinsicSize.width.toInt(), intrinsicSize.height.toInt())
                }
            },
        )
        return
    }

    val geometry = remember(size) { CropGeometry(size.width.toFloat(), size.height.toFloat()) }
    val cropRect = geometry.centeredRect(edit)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(cropRect.width / cropRect.height)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = constraints.maxWidth / cropRect.width
        ChalkakImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .requiredSize(
                    width = with(density) { (geometry.width * scale).toDp() },
                    height = with(density) { (geometry.height * scale).toDp() },
                ).graphicsLayer {
                    rotationZ = edit.rotationDegrees
                    translationX = -cropRect.centerX * scale
                    translationY = -cropRect.centerY * scale
                },
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            loadedPainter = adjustedPainter,
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun EditedPhotoImagePreview() {
    ChalkakTheme {
        EditedPhotoImage(
            model = R.drawable.preview_photo,
            edit = PhotoEdit(
                quarterTurns = 1,
                crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 1f, height = 0.6f),
                filter = PhotoFilter.Mono,
                adjustmentLayers = PhotoAdjustmentLayers(manual = PhotoAdjustments(contrast = 0.3f)),
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            imageSize = PhotoImageSize(width = 4, height = 3),
        )
    }
}
