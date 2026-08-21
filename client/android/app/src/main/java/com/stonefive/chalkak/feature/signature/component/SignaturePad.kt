package com.stonefive.chalkak.feature.signature.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.signature.SignaturePoint
import com.stonefive.chalkak.feature.signature.SignatureStroke

@Composable
internal fun SignaturePad(
    strokes: List<SignatureStroke>,
    enabled: Boolean,
    onStrokeStarted: (SignaturePoint) -> Unit,
    onStrokeMoved: (SignaturePoint) -> Unit,
    onStrokeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = ChalkakTheme.colors.textPrimary
        .copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .clip(ChalkakTheme.shapes.large)
            .background(ChalkakTheme.colors.inputBackground)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = ChalkakTheme.shapes.large,
            ).padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignatureCanvas(
            strokes = strokes,
            enabled = enabled,
            onStrokeStarted = onStrokeStarted,
            onStrokeMoved = onStrokeMoved,
            onStrokeFinished = onStrokeFinished,
            borderColor = borderColor,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Text(
            text = "여기에 손가락으로 그리기",
            modifier = Modifier.padding(top = 12.dp),
            color = ChalkakTheme.colors.textMuted,
            style = ChalkakTheme.typography.footnote,
        )
    }
}

@Composable
private fun SignatureCanvas(
    strokes: List<SignatureStroke>,
    enabled: Boolean,
    onStrokeStarted: (SignaturePoint) -> Unit,
    onStrokeMoved: (SignaturePoint) -> Unit,
    onStrokeFinished: () -> Unit,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val signatureColor = ChalkakTheme.colors.textPrimary
    val currentOnStrokeStarted by rememberUpdatedState(onStrokeStarted)
    val currentOnStrokeMoved by rememberUpdatedState(onStrokeMoved)
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val gestureModifier = if (enabled) {
        Modifier.pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                currentOnStrokeStarted(down.position.toSignaturePoint(size.width, size.height))
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && change.positionChanged()) {
                            change.consume()
                            currentOnStrokeMoved(
                                change.position.toSignaturePoint(size.width, size.height),
                            )
                        }
                    } while (change?.pressed == true)
                } finally {
                    currentOnStrokeFinished()
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .testTag(SIGNATURE_PAD_TAG)
            .semantics { contentDescription = "사인 입력창" }
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val borderWidth = 1.dp.toPx()
            drawRoundRect(
                color = borderColor,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(
                    width = borderWidth,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                    ),
                ),
            )

            strokes.forEach { stroke ->
                if (stroke.points.size == 1) {
                    val point = stroke.points
                        .first()
                        .toOffset(size.width, size.height)
                    drawCircle(
                        color = signatureColor,
                        radius = 2.dp.toPx(),
                        center = point,
                    )
                } else if (stroke.points.isNotEmpty()) {
                    drawPath(
                        path = stroke.toSmoothPath(size.width, size.height),
                        color = signatureColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        if (strokes.none { it.points.isNotEmpty() }) {
            Text(
                text = "사인",
                color = ChalkakTheme.colors.textInactive,
                style = ChalkakTheme.typography.handwriting.copy(
                    fontSize = 48.sp,
                    lineHeight = 52.sp,
                ),
            )
        }
    }
}

private fun Offset.toSignaturePoint(
    width: Int,
    height: Int,
): SignaturePoint = SignaturePoint(
    xRatio = x.div(width.coerceAtLeast(1)).coerceIn(0f, 1f),
    yRatio = y.div(height.coerceAtLeast(1)).coerceIn(0f, 1f),
)

private fun SignaturePoint.toOffset(
    width: Float,
    height: Float,
): Offset = Offset(
    x = xRatio * width,
    y = yRatio * height,
)

private fun SignatureStroke.toSmoothPath(
    width: Float,
    height: Float,
): Path = Path().apply {
    val offsets = points.map { it.toOffset(width, height) }
    moveTo(offsets.first().x, offsets.first().y)
    for (index in 1 until offsets.lastIndex) {
        val current = offsets[index]
        val next = offsets[index + 1]
        quadraticTo(
            current.x,
            current.y,
            (current.x + next.x) / 2f,
            (current.y + next.y) / 2f,
        )
    }
    lineTo(offsets.last().x, offsets.last().y)
}

private const val SIGNATURE_PAD_TAG = "signaturePad"

@Preview(
    name = "Empty",
    showBackground = true,
    widthDp = 390,
)
@Composable
private fun SignaturePadEmptyPreview() {
    ChalkakTheme {
        SignaturePadPreview(strokes = emptyList())
    }
}

@Preview(
    name = "Signed",
    showBackground = true,
    widthDp = 390,
)
@Composable
private fun SignaturePadSignedPreview() {
    ChalkakTheme {
        SignaturePadPreview(
            strokes = listOf(
                SignatureStroke(
                    points = listOf(
                        SignaturePoint(0.30f, 0.58f),
                        SignaturePoint(0.35f, 0.48f),
                        SignaturePoint(0.40f, 0.56f),
                        SignaturePoint(0.46f, 0.44f),
                        SignaturePoint(0.52f, 0.58f),
                        SignaturePoint(0.60f, 0.48f),
                        SignaturePoint(0.68f, 0.55f),
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun SignaturePadPreview(strokes: List<SignatureStroke>) {
    SignaturePad(
        strokes = strokes,
        enabled = true,
        onStrokeStarted = {},
        onStrokeMoved = {},
        onStrokeFinished = {},
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}
