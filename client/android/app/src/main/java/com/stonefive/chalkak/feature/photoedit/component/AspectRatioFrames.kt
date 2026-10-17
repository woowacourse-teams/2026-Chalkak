package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.photoedit.CropAspectRatio
import com.stonefive.chalkak.feature.photoedit.label

/**
 * 자르기 비율 선택. 비율마다 그 모양의 프레임과 이름을 함께 보여 준다.
 * [originalAspectRatio]는 원본 프레임 모양에 쓰고, 모르면 가로 사진 모양으로 그린다.
 */
@Composable
fun AspectRatioFrames(
    selectedAspectRatio: CropAspectRatio,
    originalAspectRatio: Float?,
    onAspectRatioSelect: (CropAspectRatio) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CropAspectRatio.entries.forEach { aspectRatio ->
            AspectRatioFrame(
                aspectRatio = aspectRatio,
                frameRatio = when (aspectRatio) {
                    CropAspectRatio.Free -> null
                    CropAspectRatio.Original -> originalAspectRatio ?: FALLBACK_ORIGINAL_RATIO
                    else -> aspectRatio.ratio
                },
                selected = aspectRatio == selectedAspectRatio,
                onClick = { onAspectRatioSelect(aspectRatio) },
            )
        }
    }
}

@Composable
private fun AspectRatioFrame(
    aspectRatio: CropAspectRatio,
    frameRatio: Float?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) ChalkakTheme.colors.photoEditContent else ChalkakTheme.colors.photoEditContentMuted
    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ).padding(horizontal = ChalkakTheme.spacing.xs, vertical = ChalkakTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
    ) {
        Canvas(modifier = Modifier.size(FrameBoxSize)) {
            if (frameRatio == null) {
                drawFreeFrame(color)
            } else {
                drawRatioFrame(frameRatio, color)
            }
        }
        Text(
            text = aspectRatio.label,
            style = ChalkakTheme.typography.caption,
            color = color,
        )
    }
}

private fun DrawScope.drawRatioFrame(
    ratio: Float,
    color: Color,
) {
    val strokeWidth = FrameStrokeWidth.toPx()
    val available = size.minDimension - strokeWidth
    val frameSize = if (ratio >= 1f) {
        Size(available, available / ratio)
    } else {
        Size(available * ratio, available)
    }
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - frameSize.width) / 2f, (size.height - frameSize.height) / 2f),
        size = frameSize,
        cornerRadius = CornerRadius(FrameCornerRadius.toPx()),
        style = Stroke(width = strokeWidth),
    )
}

/** 자유 비율은 정해진 모양이 없으므로 네 모서리만 그린다. */
private fun DrawScope.drawFreeFrame(color: Color) {
    val strokeWidth = FrameStrokeWidth.toPx()
    val inset = strokeWidth / 2f
    val arm = size.minDimension * FREE_FRAME_ARM_RATIO
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    listOf(
        Triple(Offset(left, top), 1f, 1f),
        Triple(Offset(right, top), -1f, 1f),
        Triple(Offset(left, bottom), 1f, -1f),
        Triple(Offset(right, bottom), -1f, -1f),
    ).forEach { (corner, directionX, directionY) ->
        drawLine(color, corner, corner + Offset(arm * directionX, 0f), strokeWidth, StrokeCap.Round)
        drawLine(color, corner, corner + Offset(0f, arm * directionY), strokeWidth, StrokeCap.Round)
    }
}

private const val FALLBACK_ORIGINAL_RATIO = 4f / 3f
private const val FREE_FRAME_ARM_RATIO = 0.3f
private val FrameBoxSize = 28.dp
private val FrameStrokeWidth = 1.5.dp
private val FrameCornerRadius = 2.dp

@Preview(widthDp = 402)
@Composable
private fun AspectRatioFramesPreview() {
    ChalkakTheme {
        AspectRatioFrames(
            selectedAspectRatio = CropAspectRatio.Portrait4x5,
            originalAspectRatio = 3f / 4f,
            onAspectRatioSelect = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground)
                .padding(vertical = ChalkakTheme.spacing.sm),
        )
    }
}
