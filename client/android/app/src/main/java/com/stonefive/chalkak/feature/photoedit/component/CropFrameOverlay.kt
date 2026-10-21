package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** 자르기 프레임. 프레임 밖을 어둡게 덮고 모서리 핸들을 그리며, 조절 중에는 3분할 격자를 보여 준다. */
@Composable
fun CropFrameOverlay(
    frame: Rect,
    modifier: Modifier = Modifier,
    showGrid: Boolean = false,
) {
    val scrimColor = ChalkakTheme.colors.photoEditScrim
    val lineColor = ChalkakTheme.colors.photoEditContent
    val gridColor = ChalkakTheme.colors.photoEditContentMuted

    Canvas(modifier = modifier) {
        drawScrimOutside(frame, scrimColor)

        if (showGrid) {
            val gridStroke = CropGridStrokeWidth.toPx()
            for (index in 1 until GRID_DIVISIONS) {
                val x = frame.left + frame.width * index / GRID_DIVISIONS
                val y = frame.top + frame.height * index / GRID_DIVISIONS
                drawLine(gridColor, Offset(x, frame.top), Offset(x, frame.bottom), gridStroke)
                drawLine(gridColor, Offset(frame.left, y), Offset(frame.right, y), gridStroke)
            }
        }

        drawRect(
            color = lineColor,
            topLeft = frame.topLeft,
            size = frame.size,
            style = Stroke(width = CropFrameStrokeWidth.toPx()),
        )

        val handleLength = CropHandleLength.toPx()
        val handleStroke = CropHandleStrokeWidth.toPx()
        val inset = handleStroke / 2f
        listOf(
            Triple(frame.topLeft, 1f, 1f),
            Triple(frame.topRight, -1f, 1f),
            Triple(frame.bottomLeft, 1f, -1f),
            Triple(frame.bottomRight, -1f, -1f),
        ).forEach { (corner, directionX, directionY) ->
            val origin = Offset(corner.x + directionX * inset, corner.y + directionY * inset)
            drawLine(lineColor, origin, Offset(origin.x + directionX * handleLength, origin.y), handleStroke)
            drawLine(lineColor, origin, Offset(origin.x, origin.y + directionY * handleLength), handleStroke)
        }
    }
}

private fun DrawScope.drawScrimOutside(
    frame: Rect,
    color: Color,
) {
    drawRect(color, Offset.Zero, Size(size.width, frame.top))
    drawRect(color, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
    drawRect(color, Offset(0f, frame.top), Size(frame.left, frame.height))
    drawRect(color, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
}

private const val GRID_DIVISIONS = 3
private val CropFrameStrokeWidth = 1.dp
private val CropGridStrokeWidth = 0.5.dp
private val CropHandleStrokeWidth = 3.dp
private val CropHandleLength = 20.dp

@Preview(widthDp = 300, heightDp = 300)
@Composable
private fun CropFrameOverlayPreview() {
    ChalkakTheme {
        CropFrameOverlay(
            frame = Rect(left = 60f, top = 80f, right = 680f, bottom = 560f),
            modifier = Modifier
                .size(300.dp)
                .background(ChalkakTheme.colors.photoEditContentMuted),
            showGrid = true,
        )
    }
}
