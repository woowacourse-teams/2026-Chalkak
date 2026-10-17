package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.feature.photoedit.CropCorner
import com.stonefive.chalkak.feature.photoedit.CropGeometry
import com.stonefive.chalkak.feature.photoedit.PhotoImageSize
import com.stonefive.chalkak.feature.photoedit.toColorFilter

/**
 * 회전한 사진 전체와 자르기 프레임. 사진은 화면 크기로 불러온 이미지를 `graphicsLayer`로 돌려 그린다.
 * 끌기 값은 화면 픽셀을 [CropGeometry] 단위로 바꿔 넘긴다.
 */
@Composable
fun CropCanvas(
    imageModel: Any?,
    edit: PhotoEdit,
    imageSize: PhotoImageSize?,
    onImageLoaded: (Int, Int) -> Unit,
    onCropMove: (Float, Float) -> Unit,
    onCropResize: (CropCorner, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (imageSize == null) {
            ChalkakImage(
                model = imageModel,
                contentDescription = "편집할 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val intrinsicSize = state.painter.intrinsicSize
                    onImageLoaded(intrinsicSize.width.toInt(), intrinsicSize.height.toInt())
                },
            )
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val geometry = remember(imageSize) { CropGeometry(imageSize.width.toFloat(), imageSize.height.toFloat()) }
        val inset = with(density) { CropCanvasInset.toPx() }
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val scale = geometry.fitScale(
            rotationDegrees = edit.rotationDegrees,
            viewportWidth = viewportWidth - inset * 2,
            viewportHeight = viewportHeight - inset * 2,
        )
        val cropRect = geometry.outputRect(edit)
        val frameWidth = cropRect.width * scale
        val frameHeight = cropRect.height * scale
        val frame = Rect(
            offset = Offset(
                x = viewportWidth / 2f + cropRect.centerX * scale - frameWidth / 2f,
                y = viewportHeight / 2f + cropRect.centerY * scale - frameHeight / 2f,
            ),
            size = Size(frameWidth, frameHeight),
        )
        val colorFilter = remember(edit.filter) { edit.filter.toColorFilter() }
        var isAdjusting by remember { mutableStateOf(false) }
        val currentFrame by rememberUpdatedState(frame)
        val currentScale by rememberUpdatedState(scale)
        val currentOnCropMove by rememberUpdatedState(onCropMove)
        val currentOnCropResize by rememberUpdatedState(onCropResize)
        val touchRadius = with(density) { CropHandleTouchRadius.toPx() }

        ChalkakImage(
            model = imageModel,
            contentDescription = "편집할 사진",
            modifier = Modifier
                .requiredSize(
                    width = with(density) { (geometry.width * scale).toDp() },
                    height = with(density) { (geometry.height * scale).toDp() },
                ).graphicsLayer { rotationZ = edit.rotationDegrees },
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
        )

        CropFrameOverlay(
            frame = frame,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(touchRadius) {
                    var target: CropDragTarget? = null
                    detectDragGestures(
                        onDragStart = { position ->
                            target = currentFrame.hitTest(position, touchRadius)
                            isAdjusting = target != null
                        },
                        onDragEnd = {
                            target = null
                            isAdjusting = false
                        },
                        onDragCancel = {
                            target = null
                            isAdjusting = false
                        },
                        onDrag = { change, dragAmount ->
                            val dragTarget = target ?: return@detectDragGestures
                            change.consume()
                            val deltaX = dragAmount.x / currentScale
                            val deltaY = dragAmount.y / currentScale
                            when (dragTarget) {
                                CropDragTarget.Move -> currentOnCropMove(deltaX, deltaY)
                                is CropDragTarget.Corner -> currentOnCropResize(dragTarget.corner, deltaX, deltaY)
                            }
                        },
                    )
                },
            showGrid = isAdjusting,
        )
    }
}

private sealed interface CropDragTarget {
    data object Move : CropDragTarget

    data class Corner(val corner: CropCorner) : CropDragTarget
}

private fun Rect.hitTest(
    position: Offset,
    touchRadius: Float,
): CropDragTarget? {
    val corners = listOf(
        CropCorner.TopStart to topLeft,
        CropCorner.TopEnd to topRight,
        CropCorner.BottomStart to bottomLeft,
        CropCorner.BottomEnd to bottomRight,
    )
    corners
        .minByOrNull { (_, point) -> (point - position).getDistance() }
        ?.takeIf { (_, point) -> (point - position).getDistance() <= touchRadius }
        ?.let { (corner, _) -> return CropDragTarget.Corner(corner) }
    return if (contains(position)) CropDragTarget.Move else null
}

// 모서리 핸들이 화면 끝에 붙어 잘리거나 시스템 제스처와 겹치지 않도록 사진 둘레에 두는 여백
private val CropCanvasInset = 24.dp
private val CropHandleTouchRadius = 32.dp

@Preview(widthDp = 360, heightDp = 480)
@Composable
private fun CropCanvasPreview() {
    ChalkakTheme {
        CropCanvas(
            imageModel = R.drawable.preview_photo,
            edit = PhotoEdit(
                straightenDegrees = 8f,
                crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 0.7f, height = 0.7f),
            ),
            imageSize = PhotoImageSize(width = 4, height = 3),
            onImageLoaded = { _, _ -> },
            onCropMove = { _, _ -> },
            onCropResize = { _, _, _ -> },
        )
    }
}
