package com.stonefive.chalkak.feature.upload

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.time.LocalDate
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PhotoUploadRoute(
    topicDate: LocalDate,
    onBack: () -> Unit,
    onSubmitted: (PhotoUploadSubmission) -> Unit,
    modifier: Modifier = Modifier,
    onReauthenticationRequired: () -> Unit = {},
    viewModel: PhotoUploadViewModel = viewModel(
        key = "photo-upload-$topicDate",
        factory = PhotoUploadViewModel.factory(topicDate),
    ),
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

                PhotoUploadUiEvent.ReauthenticationRequired -> onReauthenticationRequired()
            }
        }
    }

    LaunchedEffect(uiState.completedSubmission) {
        uiState.completedSubmission?.let { submission ->
            onSubmitted(submission)
            viewModel.reset()
        }
    }

    PhotoUploadScreen(
        uiState = uiState.copy(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoUploadScreen(
    uiState: PhotoUploadUiState,
    onAction: (PhotoUploadUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val captionScrollState = rememberScrollState()
    var isCaptionFocused by remember { mutableStateOf(false) }
    var isBackPending by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isCaptionFocused, imeTargetBottom) {
        if (isCaptionFocused && imeTargetBottom > 0) {
            snapshotFlow { captionScrollState.maxValue }
                .collectLatest(captionScrollState::scrollTo)
        }
    }

    LaunchedEffect(isBackPending, imeTargetBottom) {
        if (isBackPending && imeTargetBottom == 0) {
            withFrameNanos { }
            isBackPending = false
            onAction(PhotoUploadUiAction.BackClicked)
        }
    }

    fun requestBack() {
        if (isBackPending) return

        if (isCaptionFocused || imeBottom > 0) {
            isBackPending = true
            focusManager.clearFocus()
            keyboardController?.hide()
        } else {
            onAction(PhotoUploadUiAction.BackClicked)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(focusManager, keyboardController) {
                detectTapGestures {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PhotoUploadTopBar(
                onBackClick = ::requestBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = 8.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 8.dp,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 104.dp)
                    .imePadding()
                    .verticalScroll(captionScrollState)
                    .imeNestedScroll(),
            ) {
                PhotoUploadImageArea(
                    selectedImage = uiState.selectedImage,
                    topicTitle = uiState.topicTitle,
                    isCameraAvailable = uiState.isCameraAvailable,
                    onGalleryClick = { onAction(PhotoUploadUiAction.GalleryClicked) },
                    onCameraClick = { onAction(PhotoUploadUiAction.CameraClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(34.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                ) {
                    ChalkakTextField(
                        value = uiState.caption,
                        onValueChange = { onAction(PhotoUploadUiAction.CaptionChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isCaptionFocused = it.isFocused }
                            .testTag(PHOTO_UPLOAD_CAPTION_TAG),
                        placeholder = "작품 제목은 선택이에요.",
                        enabled = !uiState.isSubmitting,
                        textStyle = ChalkakTheme.typography.subheadline,
                        minLines = 3,
                        maxLength = CAPTION_MAX_LENGTH,
                    )

                    uiState.errorMessage?.let { errorMessage ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            modifier = Modifier
                                .fillMaxWidth(),
                            style = ChalkakTheme.typography.footnote,
                            color = ChalkakTheme.colors.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            ChalkakButton(
                text = if (uiState.isSubmitting) "전시 중..." else "전시하기",
                onClick = { onAction(PhotoUploadUiAction.SubmitClicked) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                caption = "전선 사이로 빠져나온 하늘",
            ),
            onAction = {},
        )
    }
}
