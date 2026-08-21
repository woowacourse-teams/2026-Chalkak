package com.stonefive.chalkak.feature.upload

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.upload.component.PhotoUploadImageArea
import com.stonefive.chalkak.feature.upload.component.PhotoUploadTopBar
import java.io.File

@Composable
fun PhotoUploadRoute(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    signatureModel: String? = drawableResourceUrl(R.drawable.preview_signature),
    viewModel: PhotoUploadViewModel = viewModel(factory = PhotoUploadViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPickerState = rememberPhotoPickerState(viewModel::onImageSelected)

    LaunchedEffect(viewModel, photoPickerState) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                PhotoUploadUiEvent.NavigateBack -> {
                    viewModel.reset()
                    onBack()
                }

                PhotoUploadUiEvent.OpenGallery -> photoPickerState.openGallery()

                PhotoUploadUiEvent.OpenCamera -> photoPickerState.openCamera()

                PhotoUploadUiEvent.PhotoSubmitted -> {
                    viewModel.reset()
                    onSubmitted()
                }
            }
        }
    }

    PhotoUploadScreen(
        uiState = uiState.copy(
            signatureModel = signatureModel,
            isCameraAvailable = photoPickerState.isCameraAvailable,
        ),
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Stable
private class PhotoPickerState(
    val isCameraAvailable: Boolean,
    private val launchGallery: () -> Unit,
    private val launchCamera: () -> Unit,
) {
    fun openGallery() {
        launchGallery()
    }

    fun openCamera() {
        launchCamera()
    }
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
            .background(ChalkakBackground)
            .statusBarsPadding(),
    ) {
        PhotoUploadTopBar(
            onBackClick = { onAction(PhotoUploadUiAction.BackClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 26.dp),
        )

        PhotoUploadImageArea(
            selectedImage = uiState.selectedImage,
            signatureModel = uiState.signatureModel,
            isCameraAvailable = uiState.isCameraAvailable,
            onGalleryClick = { onAction(PhotoUploadUiAction.GalleryClicked) },
            onCameraClick = { onAction(PhotoUploadUiAction.CameraClicked) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(34.dp))

        ChalkakTextField(
            value = uiState.caption,
            onValueChange = { onAction(PhotoUploadUiAction.CaptionChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .testTag(PHOTO_UPLOAD_CAPTION_TAG),
            placeholder = "작품 제목은 선택이에요.",
            textStyle = ChalkakTheme.typography.subheadline,
            minLines = 3,
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
private fun rememberPhotoPickerState(onImageSelected: (String) -> Unit): PhotoPickerState {
    val context = LocalContext.current
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)
    val isCameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCaptureFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun clearPendingCapture() {
        pendingCaptureUri = null
        pendingCaptureFilePath = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.toString()?.let(currentOnImageSelected)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val captureUri = pendingCaptureUri
        val captureFilePath = pendingCaptureFilePath
        clearPendingCapture()

        if (success && captureUri != null) {
            currentOnImageSelected(captureUri)
        } else {
            captureFilePath?.let(::File)?.delete()
        }
    }

    return remember(context, galleryLauncher, cameraLauncher, isCameraAvailable) {
        PhotoPickerState(
            isCameraAvailable = isCameraAvailable,
            launchGallery = { galleryLauncher.launch("image/*") },
            launchCamera = {
                val capture = createCameraCapture(context)
                pendingCaptureUri = capture.uri.toString()
                pendingCaptureFilePath = capture.file.absolutePath
                try {
                    cameraLauncher.launch(capture.uri)
                } catch (_: ActivityNotFoundException) {
                    clearPendingCapture()
                    capture.file.delete()
                }
            },
        )
    }
}

const val PHOTO_UPLOAD_SUBMIT_BUTTON_TAG = "photoUploadSubmitButton"
const val PHOTO_UPLOAD_CAPTION_TAG = "photoUploadCaption"

private const val CAPTION_MAX_LENGTH = 10

private fun drawableResourceUrl(@DrawableRes resourceId: Int): String =
    "android.resource://com.stonefive.chalkak/$resourceId"

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
                selectedImage = drawableResourceUrl(R.drawable.preview_photo),
                signatureModel = drawableResourceUrl(R.drawable.preview_signature),
                caption = "전선 사이로 빠져나온 하늘",
            ),
            onAction = {},
        )
    }
}
