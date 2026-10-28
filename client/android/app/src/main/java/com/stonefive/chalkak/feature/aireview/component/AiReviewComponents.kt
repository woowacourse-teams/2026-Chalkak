package com.stonefive.chalkak.feature.aireview.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun AiReviewStartSheet(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "AI 피드백을 시작할까요?",
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg)) {
        Text(title, color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.title3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.background, ChalkakTheme.shapes.large)
                .padding(ChalkakTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            FeedbackStartNote(R.drawable.ic_clock, "결과가 나오기까지 최대 1~2분 소요될 수 있어요")
            FeedbackStartNote(R.drawable.ic_smartphone, "앱을 나가도 분석은 계속되니 걱정마세요")
            FeedbackStartNote(R.drawable.ic_bell, "분석이 끝나면 알림으로 알려드릴게요")
        }
        ChalkakButton(text = "분석 시작하기", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AiReviewAudioWaveIcon(
    modifier: Modifier = Modifier,
    isAnimating: Boolean = false,
    color: Color = ChalkakTheme.colors.textPrimary,
) {
    val wavePhase = if (isAnimating) {
        rememberInfiniteTransition(label = "audio-wave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = AUDIO_WAVE_CYCLE_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "audio-wave-phase",
        )
    } else {
        null
    }
    Canvas(modifier = modifier.size(AudioWaveWidth, AudioWaveHeight).testTag("ai-review-wave")) {
        val barWidth = AudioWaveBarWidth.toPx()
        AudioWaveHeights.forEachIndexed { index, barHeight ->
            val scale = wavePhase?.value?.let { 0.7f + 0.3f * sin(it + index * AUDIO_WAVE_BAR_PHASE_STEP) } ?: 1f
            val height = barHeight.toPx() * scale
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    index * (barWidth + AudioWaveGap.toPx()),
                    (size.height - height) / 2,
                ),
                size = androidx.compose.ui.geometry
                    .Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry
                    .CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun FeedbackStartNote(
    @DrawableRes iconRes: Int,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(FeedbackStartNoteCircleSize)
                .background(ChalkakTheme.colors.surfaceElevated, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = ChalkakTheme.colors.iconSecondary,
                modifier = Modifier.size(FeedbackStartNoteIconSize),
            )
        }
        Text(
            text,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiReviewResultSheet(
    result: AiReviewContent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier,
    ) {
        AiReviewSheetDragHandle(sheetState = sheetState, onDismiss = onDismiss, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(ChalkakTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            Text(
                text = "AI 피드백이 완료되었어요",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
                fontWeight = FontWeight.SemiBold,
            )
            AiReviewResultContent(result = result)
        }
    }
}

/**
 * 다시 요청할 수 없는 실패 리뷰를 알리는 바텀 시트다. [message]에 다시 요청할 수 없는 이유를 보여 주고 "확인"으로 닫는다.
 * 다시 요청할 수 있는 실패는 이 시트 대신 시작 안내 시트를 연다.
 *
 * 예: 요청 3번을 모두 쓴 경우 "이 사진은 더 이상 AI 피드백을 요청할 수 없어요."
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiReviewFailureSheet(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier,
    ) {
        AiReviewSheetDragHandle(sheetState = sheetState, onDismiss = onDismiss, modifier = Modifier.fillMaxWidth())
        AiReviewFailureContent(
            message = message,
            onConfirm = onDismiss,
            modifier = Modifier.padding(
                start = ChalkakTheme.spacing.xl,
                end = ChalkakTheme.spacing.xl,
                bottom = ChalkakTheme.spacing.xl,
            ),
        )
    }
}

@Composable
private fun AiReviewFailureContent(
    message: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg)) {
        Text(
            text = "AI 피드백을 생성하지 못했어요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
        )
        ChalkakButton(text = "확인", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AiReviewResultContent(
    result: AiReviewContent,
    modifier: Modifier = Modifier,
) {
    var isTipVisible by rememberSaveable { mutableStateOf(false) }
    var isCriteriaDialogVisible by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg)) {
            if (result.distinctivePerspective) {
                InterpretationBadge(
                    isTipVisible = isTipVisible,
                    onClick = { isTipVisible = !isTipVisible },
                    modifier = Modifier.zIndex(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = ChalkakTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
            ) {
                Text(
                    result.feedback.summary,
                    color = ChalkakTheme.colors.textPrimary,
                    style = ChalkakTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FeedbackScoreRing(result.overallScore)
            }
            HorizontalDivider(color = ChalkakTheme.colors.border)
            ResultParagraph("좋았던 점", result.feedback.strength)
            ResultParagraph("아쉬운 점", result.feedback.weakness)
            HorizontalDivider(color = ChalkakTheme.colors.border)
            ResultParagraph("다음엔 이렇게", result.feedback.suggestion)
            ScoringCriteriaLink(
                onClick = { isCriteriaDialogVisible = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = ChalkakTheme.spacing.sm),
            )
        }
        if (isTipVisible) {
            Box(
                modifier = Modifier.matchParentSize().clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = "설명 닫기",
                    onClick = { isTipVisible = false },
                ),
            )
        }
    }
    if (isCriteriaDialogVisible) {
        AlertDialog(
            onDismissRequest = { isCriteriaDialogVisible = false },
            confirmButton = {
                TextButton(onClick = { isCriteriaDialogVisible = false }) { Text("확인") }
            },
            text = { Text(SCORING_CRITERIA_DESCRIPTION) },
        )
    }
}

@Composable
private fun ScoringCriteriaLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "찰칵AI는 어떤 기준으로 점수를 내나요?",
        modifier = modifier.clickable(
            interactionSource = null,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        color = ChalkakTheme.colors.textSecondary,
        style = ChalkakTheme.typography.footnote,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
private fun InterpretationBadge(
    isTipVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tipGap = ChalkakTheme.spacing.xs
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = "주제 해석 배지의 의미 확인",
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .background(ChalkakTheme.colors.actionPrimary, ChalkakTheme.shapes.small)
                        .padding(horizontal = ChalkakTheme.spacing.sm, vertical = ChalkakTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_aperture),
                        contentDescription = null,
                        tint = ChalkakTheme.colors.onActionPrimary,
                        modifier = Modifier.size(BadgeIconSize),
                    )
                    Text(
                        text = "작가님의 해석이 돋보이는 작품이에요",
                        color = ChalkakTheme.colors.onActionPrimary,
                        style = ChalkakTheme.typography.caption,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (isTipVisible) InterpretationTip()
        },
    ) { measurables, constraints ->
        val badge = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val tip = measurables.getOrNull(1)?.measure(Constraints())
        layout(badge.width, badge.height) {
            badge.place(0, 0)
            tip?.place(0, badge.height + tipGap.roundToPx())
        }
    }
}

@Composable
private fun InterpretationTip(modifier: Modifier = Modifier) {
    val arrowColor = ChalkakTheme.colors.actionPrimary
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .padding(start = ChalkakTheme.spacing.lg)
                .size(width = InterpretationTipArrowWidth, height = InterpretationTipArrowHeight),
        ) {
            val arrow = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2, 0f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(arrow, arrowColor)
        }
        Text(
            text = "구도 점수와 별개로, 주제 해석이 특별한 사진이라는 의미예요.",
            color = ChalkakTheme.colors.onActionPrimary,
            style = ChalkakTheme.typography.footnote,
            modifier = Modifier
                .width(InterpretationTipWidth)
                .background(ChalkakTheme.colors.actionPrimary, ChalkakTheme.shapes.button)
                .padding(ChalkakTheme.spacing.md),
        )
    }
}

@Composable
private fun FeedbackScoreRing(
    score: Double,
    modifier: Modifier = Modifier,
) {
    val trackColor = ChalkakTheme.colors.calendarCell
    val progressColor = ChalkakTheme.colors.actionPrimary
    Box(
        modifier = modifier
            .size(FeedbackScoreSize)
            .semantics { contentDescription = "전체 점수 10점 만점에 ${score.formatScore()}점" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = FeedbackScoreRingWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry
                .Size(size.width - stroke, size.height - stroke)
            drawArc(
                trackColor,
                0f,
                360f,
                false,
                androidx.compose.ui.geometry
                    .Offset(inset, inset),
                arcSize,
                style = Stroke(stroke),
            )
            drawArc(
                progressColor,
                -90f,
                360f * score.toFloat() / MAX_SCORE,
                false,
                androidx.compose.ui.geometry
                    .Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(score.formatScore(), color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.title1)
    }
}

@Composable
private fun ResultParagraph(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm)) {
        Text(title, style = ChalkakTheme.typography.footnote, fontWeight = FontWeight.SemiBold)
        Text(body, style = ChalkakTheme.typography.subheadline, color = ChalkakTheme.colors.textSecondary)
    }
}

private fun Double.formatScore(): String = String.format(Locale.KOREAN, "%.1f", this)

private val FeedbackStartNoteCircleSize = 36.dp
private val FeedbackStartNoteIconSize = 18.dp
private val FeedbackScoreSize = 84.dp
private val FeedbackScoreRingWidth = 8.dp
private val BadgeIconSize = 14.dp
private const val MAX_SCORE = 10f
private const val SCORING_CRITERIA_DESCRIPTION =
    "찰캌에서는 Nikon, PPA(Professional Photographers of America), Canon 등의 기준으로 " +
        "학습된 AI를 통해 피드백을 해줘요!"
private val AudioWaveHeights = listOf(9.dp, 15.dp, 11.dp, 18.dp, 8.dp)
private val AudioWaveWidth = 18.dp
private val AudioWaveHeight = 18.dp
private val AudioWaveBarWidth = 2.dp
private val AudioWaveGap = 2.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun AiReviewComponentsPreview() {
    ChalkakTheme {
        AiReviewResultContent(
            result = AiReviewContent(
                overallScore = 8.7,
                criteria = emptyList(),
                distinctivePerspective = true,
                feedback = AiReviewFeedback("요약", "장점", "아쉬움", "제안"),
            ),
            modifier = Modifier.padding(ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun AiReviewFailureContentPreview() {
    ChalkakTheme {
        AiReviewFailureContent(
            message = "이 사진은 더 이상 AI 피드백을 요청할 수 없어요.",
            onConfirm = {},
            modifier = Modifier.padding(ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun AiReviewStartSheetPreview() {
    ChalkakTheme {
        AiReviewStartSheet(onStart = {}, modifier = Modifier.padding(ChalkakTheme.spacing.xl))
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun AiReviewResultSheetPreview() {
    ChalkakTheme {
        AiReviewResultSheet(
            result = previewResult,
            onDismiss = {},
        )
    }
}

private val previewResult = AiReviewContent(
    overallScore = 8.7,
    criteria = emptyList(),
    distinctivePerspective = true,
    feedback = AiReviewFeedback("요약", "장점", "아쉬움", "제안"),
)

private val InterpretationTipWidth = 240.dp
private val InterpretationTipArrowWidth = 12.dp
private val InterpretationTipArrowHeight = 6.dp

private const val AUDIO_WAVE_CYCLE_MILLIS = 900
private const val AUDIO_WAVE_BAR_PHASE_STEP = 1.2f
private val AiReviewDragHandleTouchSize = 48.dp
private val AiReviewDragHandleWidth = 32.dp
private val AiReviewDragHandleHeight = 4.dp

/** Keeps the 48dp touch target while restricting the indication to the visible handle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiReviewSheetDragHandle(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(AiReviewDragHandleTouchSize)
                .testTag("ai-review-drag-handle-touch")
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (sheetState.currentValue == SheetValue.PartiallyExpanded) "시트 펼치기" else "시트 닫기",
                    onClick = {
                        scope.launch {
                            if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                                sheetState.expand()
                            } else {
                                sheetState.hide()
                                if (!sheetState.isVisible) onDismiss()
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AiReviewDragHandleWidth, AiReviewDragHandleHeight)
                    .clip(CircleShape)
                    .background(ChalkakTheme.colors.iconSecondary)
                    .indication(interactionSource, ripple(bounded = false)),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AiReviewSheetDragHandlePreview() {
    ChalkakTheme {
        AiReviewSheetDragHandle(sheetState = rememberModalBottomSheetState(), onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AiReviewAudioWaveIconPreview() {
    ChalkakTheme {
        AiReviewAudioWaveIcon()
    }
}
