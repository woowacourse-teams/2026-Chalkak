package com.stonefive.chalkak.feature.photoedit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.feature.photoedit.component.AdjustmentControls
import com.stonefive.chalkak.feature.photoedit.component.AspectRatioFrames
import com.stonefive.chalkak.feature.photoedit.component.CropCanvas
import com.stonefive.chalkak.feature.photoedit.component.EditedPhotoImage
import com.stonefive.chalkak.feature.photoedit.component.FilterStrip
import com.stonefive.chalkak.feature.photoedit.component.PhotoEditTabBar
import com.stonefive.chalkak.feature.photoedit.component.PhotoEditTopBar

/**
 * 사진 편집 화면. 내비게이션 목적지가 아니라 업로드 화면 안에서 전체 화면으로 띄운다.
 * [sessionId]마다 새 편집 초안을 만들고, 뒤로 가기는 취소와 같다.
 */
@Composable
fun PhotoEditRoute(
    imageUri: String,
    initialEdit: PhotoEdit,
    sessionId: Long,
    onEditCompleted: (PhotoEdit) -> Unit,
    onEditCanceled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoEditViewModel = viewModel(
        key = "photo-edit-$sessionId",
        factory = PhotoEditViewModel.factory(imageUri, initialEdit),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnEditCompleted by rememberUpdatedState(onEditCompleted)
    val cancel = {
        viewModel.onCancel()
        onEditCanceled()
    }

    BackHandler(onBack = cancel)
    LightSystemBarIconsEffect()

    LaunchedEffect(uiState.completedEdit) {
        uiState.completedEdit?.let(currentOnEditCompleted)
    }

    PhotoEditScreen(
        uiState = uiState,
        onCancelClick = cancel,
        onDoneClick = viewModel::onDoneClick,
        onTabSelect = viewModel::onTabSelect,
        onImageLoaded = viewModel::onImageLoaded,
        onCropMove = viewModel::onCropMove,
        onCropResize = viewModel::onCropResize,
        onRotateClick = viewModel::onRotateClick,
        onAspectRatioSelect = viewModel::onAspectRatioSelect,
        onResetClick = viewModel::onResetClick,
        onAdjustItemSelect = viewModel::onAdjustItemSelect,
        onAdjustmentChange = viewModel::onAdjustmentChange,
        onAdjustmentsResetClick = viewModel::onAdjustmentsResetClick,
        onFilterSelect = viewModel::onFilterSelect,
        modifier = modifier,
    )
}

@Composable
fun PhotoEditScreen(
    uiState: PhotoEditUiState,
    onCancelClick: () -> Unit,
    onDoneClick: () -> Unit,
    onTabSelect: (PhotoEditTab) -> Unit,
    onImageLoaded: (Int, Int) -> Unit,
    onCropMove: (Float, Float) -> Unit,
    onCropResize: (CropCorner, Float, Float) -> Unit,
    onRotateClick: () -> Unit,
    onAspectRatioSelect: (CropAspectRatio) -> Unit,
    onResetClick: () -> Unit,
    onAdjustItemSelect: (PhotoAdjustItem) -> Unit,
    onAdjustmentChange: (Float) -> Unit,
    onAdjustmentsResetClick: () -> Unit,
    onFilterSelect: (PhotoFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.photoEditBackground)
            .systemBarsPadding(),
    ) {
        PhotoEditTopBar(
            onCancelClick = onCancelClick,
            onDoneClick = onDoneClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.sm, vertical = ChalkakTheme.spacing.xs),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.selectedTab) {
                PhotoEditTab.Crop -> CropCanvas(
                    imageModel = uiState.imageUri,
                    edit = uiState.edit,
                    imageSize = uiState.imageSize,
                    onImageLoaded = onImageLoaded,
                    onCropMove = onCropMove,
                    onCropResize = onCropResize,
                    modifier = Modifier.fillMaxSize(),
                )

                PhotoEditTab.Adjust, PhotoEditTab.Filter -> EditedPhotoImage(
                    model = uiState.imageUri,
                    edit = uiState.edit,
                    contentDescription = "편집한 사진",
                    modifier = Modifier.padding(ChalkakTheme.spacing.xl),
                    imageSize = uiState.imageSize,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PhotoEditControlsHeight),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.selectedTab) {
                PhotoEditTab.Crop -> CropControls(
                    uiState = uiState,
                    onRotateClick = onRotateClick,
                    onAspectRatioSelect = onAspectRatioSelect,
                    onResetClick = onResetClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                PhotoEditTab.Adjust -> AdjustmentControls(
                    adjustmentLayers = uiState.edit.adjustmentLayers,
                    selectedItem = uiState.selectedAdjustItem,
                    canReset = uiState.canResetAdjustments,
                    onItemSelect = onAdjustItemSelect,
                    onValueChange = onAdjustmentChange,
                    onResetClick = onAdjustmentsResetClick,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = ChalkakTheme.spacing.lg),
                )

                PhotoEditTab.Filter -> FilterStrip(
                    imageModel = uiState.imageUri,
                    selectedFilter = uiState.edit.filter,
                    onFilterSelect = onFilterSelect,
                    modifier = Modifier.fillMaxWidth(),
                    adjustments = uiState.edit.adjustments,
                    contentPadding = PaddingValues(horizontal = ChalkakTheme.spacing.lg),
                )
            }
        }

        PhotoEditTabBar(
            selectedTab = uiState.selectedTab,
            onTabSelect = onTabSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CropControls(
    uiState: PhotoEditUiState,
    onRotateClick: () -> Unit,
    onAspectRatioSelect: (CropAspectRatio) -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
    ) {
        AspectRatioFrames(
            selectedAspectRatio = uiState.aspectRatio,
            originalAspectRatio = uiState.originalAspectRatio,
            onAspectRatioSelect = onAspectRatioSelect,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ChalkakTheme.spacing.sm),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRotateClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_rotate_right),
                    contentDescription = "90도 회전",
                    tint = ChalkakTheme.colors.photoEditContent,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "초기화",
                modifier = Modifier
                    .clickable(
                        enabled = uiState.canReset,
                        role = Role.Button,
                        onClick = onResetClick,
                    ).padding(horizontal = ChalkakTheme.spacing.sm, vertical = ChalkakTheme.spacing.sm),
                style = ChalkakTheme.typography.footnote,
                color = if (uiState.canReset) {
                    ChalkakTheme.colors.photoEditContent
                } else {
                    ChalkakTheme.colors.photoEditContentMuted
                },
            )
        }
    }
}

/** 어두운 편집 화면에서 상태 표시줄 아이콘이 보이도록 밝은 아이콘으로 바꾸고, 나갈 때 되돌린다. */
@Composable
private fun LightSystemBarIconsEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context
            .findActivity()
            ?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            previousLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val PhotoEditControlsHeight = 156.dp

private fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun PhotoEditScreenCropPreview() {
    ChalkakTheme {
        PhotoEditScreen(
            uiState = PhotoEditUiState(
                imageUri = drawableResourceUrl(R.drawable.preview_photo),
                edit = PhotoEdit(
                    crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 0.8f, height = 0.8f),
                ),
                imageSize = PhotoImageSize(width = 4, height = 3),
                aspectRatio = CropAspectRatio.Square,
                lockedAspectRatio = 1f,
            ),
            onCancelClick = {},
            onDoneClick = {},
            onTabSelect = {},
            onImageLoaded = { _, _ -> },
            onCropMove = { _, _ -> },
            onCropResize = { _, _, _ -> },
            onRotateClick = {},
            onAspectRatioSelect = {},
            onResetClick = {},
            onAdjustItemSelect = {},
            onAdjustmentChange = {},
            onAdjustmentsResetClick = {},
            onFilterSelect = {},
        )
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun PhotoEditScreenAdjustPreview() {
    ChalkakTheme {
        PhotoEditScreen(
            uiState = PhotoEditUiState(
                imageUri = drawableResourceUrl(R.drawable.preview_photo),
                edit = PhotoEdit(
                    adjustmentLayers = PhotoAdjustmentLayers(
                        automatic = PhotoAdjustments(exposure = 0.2f, shadows = 0.3f),
                        intensity = 1f,
                    ),
                ),
                imageSize = PhotoImageSize(width = 4, height = 3),
                selectedTab = PhotoEditTab.Adjust,
            ),
            onCancelClick = {},
            onDoneClick = {},
            onTabSelect = {},
            onImageLoaded = { _, _ -> },
            onCropMove = { _, _ -> },
            onCropResize = { _, _, _ -> },
            onRotateClick = {},
            onAspectRatioSelect = {},
            onResetClick = {},
            onAdjustItemSelect = {},
            onAdjustmentChange = {},
            onAdjustmentsResetClick = {},
            onFilterSelect = {},
        )
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun PhotoEditScreenFilterPreview() {
    ChalkakTheme {
        PhotoEditScreen(
            uiState = PhotoEditUiState(
                imageUri = drawableResourceUrl(R.drawable.preview_photo),
                edit = PhotoEdit(filter = PhotoFilter.Warm),
                imageSize = PhotoImageSize(width = 4, height = 3),
                selectedTab = PhotoEditTab.Filter,
            ),
            onCancelClick = {},
            onDoneClick = {},
            onTabSelect = {},
            onImageLoaded = { _, _ -> },
            onCropMove = { _, _ -> },
            onCropResize = { _, _, _ -> },
            onRotateClick = {},
            onAspectRatioSelect = {},
            onResetClick = {},
            onAdjustItemSelect = {},
            onAdjustmentChange = {},
            onAdjustmentsResetClick = {},
            onFilterSelect = {},
        )
    }
}
