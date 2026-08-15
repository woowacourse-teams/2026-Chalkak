package com.stonefive.chalkak.feature.upload

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.upload.component.PhotoUploadImageArea
import com.stonefive.chalkak.feature.upload.component.PhotoUploadTopBar

@Composable
fun PhotoUploadRoute(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    signatureModel: Any? = R.drawable.preview_signature,
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
            signatureModel = signatureModel,
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
            signatureModel = uiState.signatureModel,
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

const val PHOTO_UPLOAD_SUBMIT_BUTTON_TAG = "photoUploadSubmitButton"
const val PHOTO_UPLOAD_CAPTION_TAG = "photoUploadCaption"

private const val CAPTION_MAX_LENGTH = 30

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
                signatureModel = R.drawable.preview_signature,
                caption = "전선 사이로 빠져나온 하늘",
            ),
            onAction = {},
        )
    }
}
