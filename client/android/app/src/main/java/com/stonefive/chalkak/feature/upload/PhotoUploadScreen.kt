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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun PhotoUploadRoute(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    signatureModel: String? = drawableResourceUrl(R.drawable.preview_signature),
    viewModel: PhotoUploadViewModel = viewModel(factory = PhotoUploadViewModel.Factory),
) {
    var pendingCameraCapture by remember { mutableStateOf<CameraCapture?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isCameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.onImageSelected(uri.toString())
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (success && capture != null) {
            viewModel.onImageSelected(capture.uri.toString())
        } else {
            capture?.file?.delete()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                PhotoUploadUiEvent.NavigateBack -> {
                    viewModel.reset()
                    onBack()
                }

                PhotoUploadUiEvent.OpenGallery -> galleryLauncher.launch("image/*")

                PhotoUploadUiEvent.OpenCamera -> {
                    val capture = createCameraCapture(context)
                    pendingCameraCapture = capture
                    try {
                        cameraLauncher.launch(capture.uri)
                    } catch (_: ActivityNotFoundException) {
                        pendingCameraCapture = null
                        capture.file.delete()
                    }
                }

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
            isCameraAvailable = isCameraAvailable,
        ),
        onAction = viewModel::onAction,
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
