package com.stonefive.chalkak.feature.upload.component

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun PhotoUploadImageArea(
    selectedImage: Any?,
    signatureModel: Any?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_AREA_ASPECT_RATIO)
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
                Text(
                    text = "주제 ‘틈’에 맞는 한 장",
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.subheadline,
                )
                Text(
                    text = "앨범에서 고르거나 지금 찍어요",
                    color = ChalkakTheme.colors.textMuted,
                    style = ChalkakTheme.typography.caption,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        } else {
            ChalkakSignedImage(
                imageModel = selectedImage,
                signatureModel = signatureModel,
                contentDescription = "선택한 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
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
            PhotoUploadActionButton(
                iconRes = R.drawable.ic_photo_camera,
                description = "카메라로 촬영",
                onClick = onCameraClick,
            )
        }
    }
}

private const val PHOTO_AREA_ASPECT_RATIO = 1.216f
