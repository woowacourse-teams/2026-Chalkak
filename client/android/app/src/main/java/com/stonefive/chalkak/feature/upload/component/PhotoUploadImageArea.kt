package com.stonefive.chalkak.feature.upload.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun PhotoUploadImageArea(
    selectedImage: String?,
    topicTitle: String?,
    isCameraAvailable: Boolean = true,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholderAspectRatio = if (selectedImage == null) {
        Modifier.aspectRatio(PHOTO_AREA_ASPECT_RATIO)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(placeholderAspectRatio)
            .background(ChalkakInputBackground),
    ) {
        if (selectedImage == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .photoUploadDashedOutline(ChalkakTheme.colors.border),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                topicTitle?.let {
                    Text(
                        text = "주제 ‘$it’에 맞는 한 장",
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
                Text(
                    text = "앨범에서 고르거나 지금 찍어요",
                    color = ChalkakTheme.colors.textMuted,
                    style = ChalkakTheme.typography.caption,
                    modifier = Modifier.padding(top = if (topicTitle == null) 0.dp else 5.dp),
                )
            }
        } else {
            ChalkakImage(
                model = selectedImage,
                contentDescription = "선택한 사진",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PhotoUploadActionButton(
                iconRes = R.drawable.ic_photo_library,
                description = "앨범에서 사진 선택",
                onClick = onGalleryClick,
            )
            if (isCameraAvailable) {
                PhotoUploadActionButton(
                    iconRes = R.drawable.ic_photo_camera,
                    description = "카메라로 촬영",
                    onClick = onCameraClick,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun PhotoUploadImageAreaEmptyPreview() {
    ChalkakTheme {
        PhotoUploadImageArea(
            selectedImage = null,
            topicTitle = "틈",
            onGalleryClick = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun PhotoUploadImageAreaSelectedPreview() {
    ChalkakTheme {
        PhotoUploadImageArea(
            selectedImage = drawableResourceUrl(R.drawable.preview_photo),
            topicTitle = "틈",
            onGalleryClick = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun PhotoUploadImageAreaWithoutCameraPreview() {
    ChalkakTheme {
        PhotoUploadImageArea(
            selectedImage = null,
            topicTitle = null,
            isCameraAvailable = false,
            onGalleryClick = {},
            onCameraClick = {},
        )
    }
}

private const val PHOTO_AREA_ASPECT_RATIO = 1.216f

private fun drawableResourceUrl(@DrawableRes resourceId: Int): String =
    "android.resource://com.stonefive.chalkak/$resourceId"

private fun Modifier.photoUploadDashedOutline(
    color: Color,
    strokeWidth: Dp = 1.dp,
): Modifier = drawBehind {
    val width = strokeWidth.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
    drawRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        style = Stroke(width = width, pathEffect = dash),
    )
}
