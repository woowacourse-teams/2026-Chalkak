package com.stonefive.chalkak.feature.upload

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite

@Composable
fun PhotoUploadRoute(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedImage by remember { mutableStateOf<Any?>(null) }
    var caption by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) selectedImage = uri
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) selectedImage = bitmap
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    PhotoUploadScreen(
        uiState = PhotoUploadUiState(
            selectedImage = selectedImage,
            caption = caption,
        ),
        onAction = { action ->
            when (action) {
                PhotoUploadUiAction.BackClicked -> onBack()

                PhotoUploadUiAction.GalleryClicked -> galleryLauncher.launch("image/*")

                PhotoUploadUiAction.CameraClicked -> {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                is PhotoUploadUiAction.CaptionChanged -> caption = action.caption

                PhotoUploadUiAction.SubmitClicked -> onSubmitted()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PhotoUploadScreen(
    uiState: PhotoUploadUiState,
    onAction: (PhotoUploadUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakBackground),
    ) {
        PhotoUploadTopBar(
            onBackClick = { onAction(PhotoUploadUiAction.BackClicked) },
        )

        PhotoUploadImageArea(
            selectedImage = uiState.selectedImage,
            onGalleryClick = { onAction(PhotoUploadUiAction.GalleryClicked) },
            onCameraClick = { onAction(PhotoUploadUiAction.CameraClicked) },
        )

        ChalkakTextField(
            value = uiState.caption,
            onValueChange = { onAction(PhotoUploadUiAction.CaptionChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = 22.dp)
                .padding(top = 34.dp)
                .testTag(PHOTO_UPLOAD_CAPTION_TAG),
            placeholder = "한 줄은 선택이에요.",
            textStyle = ChalkakTheme.typography.subheadline,
            maxLength = CAPTION_MAX_LENGTH,
        )

        Spacer(modifier = Modifier.weight(1f))

        ChalkakButton(
            text = "전시하기",
            onClick = { onAction(PhotoUploadUiAction.SubmitClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 26.dp)
                .testTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG),
            enabled = uiState.canSubmit,
        )
    }
}

@Composable
private fun PhotoUploadTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(93.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = "전시하기",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun PhotoUploadImageArea(
    selectedImage: Any?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_AREA_ASPECT_RATIO)
            .background(ChalkakInputBackground),
    ) {
        if (selectedImage == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .dashedOutline(ChalkakTheme.colors.border),
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
            ChalkakImage(
                model = selectedImage,
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

@Composable
private fun PhotoUploadActionButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(ChalkakWhite.copy(alpha = 0.86f))
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ChalkakTheme.colors.iconPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun Modifier.dashedOutline(
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

const val PHOTO_UPLOAD_SUBMIT_BUTTON_TAG = "photoUploadSubmitButton"
const val PHOTO_UPLOAD_CAPTION_TAG = "photoUploadCaption"

private const val CAPTION_MAX_LENGTH = 30
private const val PHOTO_AREA_ASPECT_RATIO = 1.216f

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun PhotoUploadScreenEmptyPreview() {
    ChalkakTheme {
        PhotoUploadScreen(
            uiState = PhotoUploadUiState(),
            onAction = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun PhotoUploadScreenSelectedPreview() {
    ChalkakTheme {
        PhotoUploadScreen(
            uiState = PhotoUploadUiState(
                selectedImage = R.drawable.preview_photo,
                caption = "전선 사이로 빠져나온 하늘",
            ),
            onAction = {},
        )
    }
}
