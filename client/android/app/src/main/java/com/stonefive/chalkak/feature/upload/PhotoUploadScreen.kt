package com.stonefive.chalkak.feature.upload

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialog
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialogStyle
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextFieldStyle
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextLengthMetric
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.photoedit.PhotoEditRoute
import com.stonefive.chalkak.feature.photoedit.component.EditedPhotoImage
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
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)
    val photoPickerState = rememberPhotoPickerState(
        onImageSelected = viewModel::onImageSelected,
        onCapturedImageSaveFailed = viewModel::onCapturedImageSaveFailed,
        onCapturedImageCompleted = viewModel::onCapturedImageCompleted,
    )

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

    val editingPhoto = uiState.editingPhoto
    if (editingPhoto != null) {
        PhotoEditRoute(
            imageUri = editingPhoto.imageUri,
            initialEdit = editingPhoto.initialEdit,
            sessionId = editingPhoto.id,
            onEditCompleted = { edit -> viewModel.onEditConfirmed(editingPhoto.imageUri, edit) },
            onEditCanceled = viewModel::onEditCanceled,
            modifier = modifier,
        )
        return
    }

    PhotoUploadScreen(
        uiState = uiState.copy(
            isCameraAvailable = photoPickerState.isCameraAvailable,
        ),
        onAction = viewModel::onAction,
        onRetryTopicLoad = viewModel::retryTopicLoad,
        onPhotoClick = viewModel::onPhotoClick,
        onDiscardConfirmed = viewModel::onDiscardConfirmed,
        onDiscardDismissed = viewModel::onDiscardDismissed,
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
    onRetryTopicLoad: () -> Unit = {},
    onPhotoClick: () -> Unit = {},
    onDiscardConfirmed: () -> Unit = {},
    onDiscardDismissed: () -> Unit = {},
) {
    if (uiState.showsDiscardConfirmation) {
        ChalkakConfirmDialog(
            title = "작성을 그만둘까요?",
            message = "뒤로 가면 작성 중인 내용이 사라져요.\n그래도 뒤로 가시겠어요?",
            confirmText = "뒤로 가기",
            onConfirm = onDiscardConfirmed,
            onDismiss = onDiscardDismissed,
            modifier = Modifier.fillMaxWidth(0.81f),
            confirmStyle = ChalkakConfirmDialogStyle.DESTRUCTIVE,
        )
    }

    if (uiState.isWriting) {
        PhotoUploadWritingContent(
            uiState = uiState,
            onAction = onAction,
            onRetryTopicLoad = onRetryTopicLoad,
            onPhotoClick = onPhotoClick,
            modifier = modifier,
        )
    } else {
        PhotoUploadPhotoContent(
            uiState = uiState,
            onAction = onAction,
            onRetryTopicLoad = onRetryTopicLoad,
            onPhotoClick = onPhotoClick,
            modifier = modifier,
        )
    }
}

/** 1단계: 사진을 고른다. 사진을 고르거나 다시 편집하면 2단계(글 쓰기)로 넘어간다. */
@Composable
private fun PhotoUploadPhotoContent(
    uiState: PhotoUploadUiState,
    onAction: (PhotoUploadUiAction) -> Unit,
    onRetryTopicLoad: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = !uiState.isSubmitting) { onAction(PhotoUploadUiAction.BackClicked) }

    PhotoUploadScaffold(
        title = "전시하기",
        onBackClick = { if (!uiState.isSubmitting) onAction(PhotoUploadUiAction.BackClicked) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ChalkakTheme.spacing.xl),
        ) {
            PhotoUploadImageArea(
                selectedImage = uiState.selectedImage,
                topicTitle = uiState.topicTitle,
                isCameraAvailable = uiState.isCameraAvailable,
                onGalleryClick = { onAction(PhotoUploadUiAction.GalleryClicked) },
                onCameraClick = { onAction(PhotoUploadUiAction.CameraClicked) },
                modifier = Modifier.fillMaxWidth(),
                selectedEdit = uiState.selectedEdit,
                isPhotoEditable = !uiState.isSubmitting,
                onPhotoClick = onPhotoClick,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PhotoUploadContentHorizontalPadding)
                    .padding(top = ChalkakTheme.spacing.xl),
            ) {
                Text(
                    text = "오늘의 주제",
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.caption,
                )
                uiState.topicTitle?.let { topicTitle ->
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xs))
                    Text(
                        text = topicTitle,
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.title3,
                        fontWeight = FontWeight.Bold,
                    )
                }
                PhotoUploadTopicError(
                    errorMessage = uiState.topicErrorMessage,
                    onRetry = onRetryTopicLoad,
                )
            }
        }
    }
}

/** 2단계: 고른 사진을 작은 썸네일로 보며 제목과 이야기를 쓴다. 둘 다 선택 입력이다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoUploadWritingContent(
    uiState: PhotoUploadUiState,
    onAction: (PhotoUploadUiAction) -> Unit,
    onRetryTopicLoad: () -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var isDescriptionFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeTargetBottom = WindowInsets.imeAnimationTarget.getBottom(density)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 이야기 칸은 화면 맨 아래 입력칸이라, 키보드가 올라오거나 줄이 늘면 끝까지 따라 내려간다.
    LaunchedEffect(isDescriptionFocused, imeTargetBottom) {
        if (isDescriptionFocused && imeTargetBottom > 0) {
            snapshotFlow { scrollState.maxValue }
                .collectLatest(scrollState::scrollTo)
        }
    }

    // 뒤로 가면 작성한 내용은 그대로 두고 사진 고르기 단계로 돌아간다.
    BackHandler(enabled = !uiState.isSubmitting) { onAction(PhotoUploadUiAction.WritingBackClicked) }

    PhotoUploadScaffold(
        title = "글 쓰기",
        onBackClick = { if (!uiState.isSubmitting) onAction(PhotoUploadUiAction.WritingBackClicked) },
        bottomButton = {
            ChalkakButton(
                text = if (uiState.isSubmitting) "전시 중..." else "전시하기",
                onClick = { onAction(PhotoUploadUiAction.SubmitClicked) },
                modifier = it.testTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG),
                enabled = uiState.canSubmit,
            )
        },
        modifier = modifier.pointerInput(focusManager, keyboardController) {
            detectTapGestures {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = PhotoUploadBottomButtonSpace)
                .imePadding()
                .verticalScroll(scrollState)
                .imeNestedScroll()
                .padding(horizontal = PhotoUploadContentHorizontalPadding)
                .padding(top = ChalkakTheme.spacing.lg),
        ) {
            // 제목 글자 수를 썸네일 아래쪽에 맞춰, 아래 구분선과의 간격을 이야기 칸의 글자 수-테두리 간격과 같게 한다.
            Row(verticalAlignment = Alignment.Bottom) {
                uiState.selectedImage?.let { image ->
                    EditedPhotoImage(
                        model = image,
                        edit = uiState.selectedEdit,
                        contentDescription = "선택한 사진",
                        modifier = Modifier
                            .size(PhotoUploadThumbnailSize)
                            .clip(ChalkakTheme.shapes.photoCard)
                            .clickable(
                                enabled = !uiState.isSubmitting,
                                onClickLabel = "사진 편집",
                                role = Role.Button,
                                onClick = onPhotoClick,
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(ChalkakTheme.spacing.lg))
                }
                Column(modifier = Modifier.weight(1f)) {
                    PhotoUploadSectionLabel(text = "제목")
                    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xs))
                    ChalkakTextField(
                        value = uiState.caption,
                        onValueChange = { onAction(PhotoUploadUiAction.CaptionChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            // 이야기 칸의 글자 수와 오른쪽 끝을 맞춘다.
                            .padding(end = ChalkakTheme.spacing.lg)
                            .testTag(PHOTO_UPLOAD_CAPTION_TAG),
                        placeholder = "사진의 제목을 적어 주세요. (선택)",
                        enabled = !uiState.isSubmitting,
                        textStyle = ChalkakTheme.typography.headline,
                        singleLine = true,
                        maxLength = CAPTION_MAX_LENGTH,
                        style = ChalkakTextFieldStyle.PLAIN,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = ChalkakTheme.spacing.lg),
                color = ChalkakTheme.colors.border,
            )

            PhotoUploadSectionLabel(text = "이야기")
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
            ChalkakTextField(
                value = uiState.description,
                onValueChange = { onAction(PhotoUploadUiAction.DescriptionChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isDescriptionFocused = it.isFocused }
                    .testTag(PHOTO_UPLOAD_DESCRIPTION_TAG),
                placeholder = "사진에 담긴 이야기를 적어 주세요. (선택)",
                enabled = !uiState.isSubmitting,
                textStyle = ChalkakTheme.typography.body,
                minLines = DESCRIPTION_MIN_LINES,
                maxLines = DESCRIPTION_MAX_LINES,
                maxLength = DESCRIPTION_MAX_LENGTH,
                // 서버가 이야기 길이를 code point로 검증하므로 같은 단위로 제한한다.
                lengthMetric = ChalkakTextLengthMetric.CODE_POINTS,
            )

            PhotoUploadTopicError(
                errorMessage = uiState.topicErrorMessage,
                onRetry = onRetryTopicLoad,
            )
        }
    }
}

@Composable
private fun PhotoUploadScaffold(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomButton: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PhotoUploadTopBar(
                onBackClick = onBackClick,
                title = title,
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
            content()

            bottomButton?.invoke(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 26.dp),
            )
        }
    }
}

@Composable
private fun PhotoUploadSectionLabel(text: String) {
    Text(
        text = text,
        color = ChalkakTheme.colors.textPrimary,
        style = ChalkakTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** 주제를 불러오지 못하면 전시할 수 없으므로 두 단계 모두에서 다시 시도할 수 있게 보여 준다. */
@Composable
private fun PhotoUploadTopicError(
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    errorMessage ?: return
    Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
    Text(
        text = errorMessage,
        modifier = Modifier.fillMaxWidth(),
        style = ChalkakTheme.typography.footnote,
        color = ChalkakTheme.colors.error,
    )
    TextButton(onClick = onRetry) {
        Text(
            text = "다시 시도",
            color = ChalkakTheme.colors.actionPrimary,
            style = ChalkakTheme.typography.body,
        )
    }
}

@Composable
private fun rememberPhotoPickerState(
    onImageSelected: (String) -> Unit,
    onCapturedImageSaveFailed: () -> Unit,
    onCapturedImageCompleted: (String, String?, Boolean) -> Unit,
): PhotoPickerState {
    val context = LocalContext.current
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)
    val currentOnCapturedImageSaveFailed by rememberUpdatedState(onCapturedImageSaveFailed)
    val currentOnCapturedImageCompleted by rememberUpdatedState(onCapturedImageCompleted)
    val isCameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCaptureFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun completeCapturedImageSelection(
        captureUri: String,
        captureFilePath: String?,
        saveToGallery: Boolean,
    ) {
        currentOnCapturedImageCompleted(captureUri, captureFilePath, saveToGallery)
    }

    fun clearPendingCapture() {
        pendingCaptureUri = null
        pendingCaptureFilePath = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.toString()?.let(currentOnImageSelected)
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val captureUri = pendingCaptureUri
        val captureFilePath = pendingCaptureFilePath
        clearPendingCapture()

        if (captureUri != null) {
            if (!isGranted) currentOnCapturedImageSaveFailed()
            completeCapturedImageSelection(
                captureUri = captureUri,
                captureFilePath = captureFilePath,
                saveToGallery = isGranted,
            )
        } else {
            captureFilePath?.let(::File)?.delete()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val captureUri = pendingCaptureUri
        val captureFilePath = pendingCaptureFilePath

        if (success && captureUri != null) {
            if (shouldRequestLegacyGalleryPermission(context)) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                clearPendingCapture()
                completeCapturedImageSelection(
                    captureUri = captureUri,
                    captureFilePath = captureFilePath,
                    saveToGallery = true,
                )
            }
        } else {
            clearPendingCapture()
            captureFilePath?.let(::File)?.delete()
        }
    }

    return remember(context, galleryLauncher, cameraLauncher, isCameraAvailable, storagePermissionLauncher) {
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
                } catch (_: SecurityException) {
                    clearPendingCapture()
                    capture.file.delete()
                }
            },
        )
    }
}

const val PHOTO_UPLOAD_SUBMIT_BUTTON_TAG = "photoUploadSubmitButton"
const val PHOTO_UPLOAD_CAPTION_TAG = "photoUploadCaption"
const val PHOTO_UPLOAD_DESCRIPTION_TAG = "photoUploadDescription"

private const val CAPTION_MAX_LENGTH = 10
private const val DESCRIPTION_MAX_LENGTH = 150
private const val DESCRIPTION_MIN_LINES = 8
private const val DESCRIPTION_MAX_LINES = 16
private val PhotoUploadContentHorizontalPadding = 22.dp
private val PhotoUploadBottomButtonSpace = 104.dp
private val PhotoUploadThumbnailSize = 84.dp

private fun drawableResourceUrl(@DrawableRes resourceId: Int): String =
    "android.resource://${BuildConfig.APPLICATION_ID}/$resourceId"

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
                description = "퇴근길에 만난 노을을 담았어요.",
                isWriting = true,
            ),
            onAction = {},
        )
    }
}
