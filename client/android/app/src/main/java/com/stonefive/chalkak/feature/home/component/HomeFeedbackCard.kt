package com.stonefive.chalkak.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.feature.home.HomeFeedbackPhase
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * 홈의 오늘 사진 아래에 놓이는 AI 피드백 카드다. [phase]에 따라 한 줄 헤더의 제목과 동작이 바뀐다.
 *
 * - [HomeFeedbackPhase.Ready]: 헤더를 누르면 [onStart]를 호출한다. 호출한 쪽이 시작 안내 시트를 연다.
 * - [HomeFeedbackPhase.Processing]: 헤더를 누를 수 없고, 오른쪽 음성 파형 아이콘만 움직인다.
 * - [HomeFeedbackPhase.Completed]: 헤더를 누르면 [onToggleExpanded]를 호출한다. [isExpanded]가 true면 결과를 펼친다.
 *
 */
@Composable
fun HomeFeedbackCard(
    phase: HomeFeedbackPhase,
    isExpanded: Boolean,
    onStart: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    result: AiReviewContent? = null,
) {
    if (phase == HomeFeedbackPhase.Hidden) return

    var isTipRequested by rememberSaveable { mutableStateOf(false) }
    val showsDetail = phase == HomeFeedbackPhase.Completed && isExpanded
    LaunchedEffect(showsDetail) {
        if (!showsDetail) isTipRequested = false
    }
    // 결과가 접히는 프레임에 아직 지워지지 않은 요청이 남아 있어도 툴팁과 닫기 오버레이를 그리지 않는다.
    val isInterpretationTipVisible = showsDetail && isTipRequested

    Box(
        modifier = modifier
            .shadow(
                elevation = FeedbackCardShadowElevation,
                shape = ChalkakTheme.shapes.xlarge,
                ambientColor = ChalkakTheme.colors.actionPrimary
                    .copy(alpha = FEEDBACK_CARD_SHADOW_ALPHA),
                spotColor = ChalkakTheme.colors.actionPrimary
                    .copy(alpha = FEEDBACK_CARD_SHADOW_ALPHA),
            ).background(ChalkakTheme.colors.surfaceElevated, ChalkakTheme.shapes.xlarge)
            .border(FeedbackCardBorderWidth, ChalkakTheme.colors.border, ChalkakTheme.shapes.xlarge),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FeedbackHeader(
                phase = phase,
                isExpanded = isExpanded,
                onClick = if (phase == HomeFeedbackPhase.Completed) onToggleExpanded else onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = showsDetail,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FeedbackDetail(
                    result = result,
                    isInterpretationTipVisible = isInterpretationTipVisible,
                    onInterpretationBadgeClick = { isTipRequested = !isTipRequested },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ChalkakTheme.spacing.xl,
                            end = ChalkakTheme.spacing.xl,
                            bottom = ChalkakTheme.spacing.xl,
                        ),
                )
            }
        }
        // 툴팁이 떠 있는 동안 카드의 어느 곳을 눌러도 툴팁만 닫히도록 카드 전체를 덮는다.
        if (isInterpretationTipVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = "설명 닫기",
                        onClick = { isTipRequested = false },
                    ),
            )
        }
    }
}

@Composable
private fun FeedbackHeader(
    phase: HomeFeedbackPhase,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (phase) {
        HomeFeedbackPhase.Hidden -> ""
        HomeFeedbackPhase.Ready -> "이 사진, AI에게 피드백 받아보기"
        HomeFeedbackPhase.Processing -> "AI 피드백이 진행중이에요"
        HomeFeedbackPhase.Completed -> "AI 피드백이 완료되었어요"
    }
    val showsDetail = phase == HomeFeedbackPhase.Completed && isExpanded
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = modifier
            .heightIn(min = FeedbackHeaderMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = phase != HomeFeedbackPhase.Processing,
                role = Role.Button,
                onClick = onClick,
            ).semantics {
                contentDescription = title
                if (phase == HomeFeedbackPhase.Completed) {
                    stateDescription = if (isExpanded) "펼쳐짐" else "접힘"
                }
            }.alpha(if (isPressed) FEEDBACK_HEADER_PRESSED_ALPHA else 1f)
            .padding(
                start = if (showsDetail) ChalkakTheme.spacing.xl else ChalkakTheme.spacing.lg,
                end = ChalkakTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = if (showsDetail) ChalkakTheme.typography.headline else ChalkakTheme.typography.callout,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (phase == HomeFeedbackPhase.Completed) {
            if (!isExpanded) {
                Text(
                    text = "자세히 보기",
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.footnote,
                )
            }
            Box(
                modifier = Modifier.size(FeedbackHeaderIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = ChalkakTheme.colors.iconSecondary,
                    modifier = Modifier.size(FeedbackChevronSize),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(FeedbackWaveCircleSize)
                    .background(ChalkakTheme.colors.actionPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AudioWaveIcon(isAnimating = phase == HomeFeedbackPhase.Processing)
            }
        }
    }
}

@Composable
private fun FeedbackDetail(
    result: AiReviewContent?,
    isInterpretationTipVisible: Boolean,
    onInterpretationBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
    ) {
        if (result?.distinctivePerspective == true) {
            InterpretationBadge(
                isTipVisible = isInterpretationTipVisible,
                onClick = onInterpretationBadgeClick,
                // 툴팁이 아래 문단 위에 그려지도록 같은 Column의 다음 형제들보다 위에 둔다.
                modifier = Modifier.zIndex(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ChalkakTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            Text(
                text = result
                    ?.feedback
                    ?.summary
                    .orEmpty(),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FeedbackScoreRing(score = result?.overallScore ?: 0.0)
        }
        HorizontalDivider(color = ChalkakTheme.colors.border)
        result?.feedback?.let { feedback ->
            FeedbackParagraph(title = "좋았던 점", body = feedback.strength)
            FeedbackParagraph(title = "아쉬운 점", body = feedback.weakness)
            HorizontalDivider(color = ChalkakTheme.colors.border)
            FeedbackParagraph(title = "다음엔 이렇게", body = feedback.suggestion)
        }
    }
}

/**
 * "작가님의 해석이 돋보이는 작품이에요" 배지와, 배지를 눌렀을 때 바로 아래에 뜨는 설명 툴팁이다.
 * 툴팁은 배지 아래로 겹쳐 그리기만 하고 자리를 차지하지 않으므로, 열고 닫아도 카드 높이가 바뀌지 않는다.
 */
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
                        modifier = Modifier.size(InterpretationBadgeIconSize),
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

/**
 * 10점 만점 점수를 원형 링으로 보여 준다. 링은 12시 방향에서 시계 방향으로 [score] / 10 만큼 채워진다.
 * 예: 8.7이면 둘레의 87%를 채운다.
 */
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
            .semantics(mergeDescendants = true) { contentDescription = "전체 점수 10점 만점에 ${score.formatScore()}점" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = FeedbackScoreRingWidth.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * score.toFloat() / FEEDBACK_MAX_SCORE,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = score.formatScore(),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )
    }
}

private fun Double.formatScore(): String = String.format(Locale.KOREAN, "%.1f", this)

@Composable
private fun FeedbackParagraph(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.footnote,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.subheadline,
        )
    }
}

/**
 * 막대 5개로 된 음성 파형 아이콘이다. [isAnimating]이 true면 막대마다 시차를 두고 높이가 70~100% 사이로 출렁인다.
 * false면 막대를 원래 높이로 멈춰 둔다.
 */
@Composable
private fun AudioWaveIcon(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val barColor = ChalkakTheme.colors.onActionPrimary
    // 매 프레임 바뀌는 값은 그리기 단계에서만 읽어 화면 전체가 다시 구성되지 않게 한다.
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
    Canvas(modifier = modifier.size(width = AudioWaveWidth, height = AudioWaveHeight)) {
        val barWidth = AudioWaveBarWidth.toPx()
        val gap = AudioWaveBarGap.toPx()
        AudioWaveBarHeights.forEachIndexed { index, barHeight ->
            val scale = wavePhase?.value?.let { 0.7f + 0.3f * sin(it + index * AUDIO_WAVE_BAR_PHASE_STEP) } ?: 1f
            val height = barHeight.toPx() * scale
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val FEEDBACK_MAX_SCORE = 10f
private const val FEEDBACK_CARD_SHADOW_ALPHA = 0.08f
private const val FEEDBACK_HEADER_PRESSED_ALPHA = 0.8f
private const val AUDIO_WAVE_CYCLE_MILLIS = 900
private const val AUDIO_WAVE_BAR_PHASE_STEP = 1.2f
private val FeedbackCardShadowElevation = 12.dp
private val FeedbackCardBorderWidth = 1.dp
private val FeedbackHeaderMinHeight = 64.dp
private val FeedbackHeaderIconSize = 44.dp
private val FeedbackChevronSize = 16.dp
private val FeedbackWaveCircleSize = 32.dp
private val FeedbackScoreSize = 84.dp
private val FeedbackScoreRingWidth = 8.dp
private val InterpretationBadgeIconSize = 14.dp
private val InterpretationTipWidth = 240.dp
private val InterpretationTipArrowWidth = 12.dp
private val InterpretationTipArrowHeight = 6.dp
private val AudioWaveBarHeights = listOf(9.dp, 15.dp, 11.dp, 18.dp, 8.dp)
private val AudioWaveBarWidth = 2.dp
private val AudioWaveBarGap = 2.dp
private val AudioWaveWidth = 18.dp
private val AudioWaveHeight = 18.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeFeedbackCardPreview() {
    ChalkakTheme {
        Column(
            modifier = Modifier.padding(ChalkakTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            HomeFeedbackPhase.entries.forEach { phase ->
                HomeFeedbackCard(
                    phase = phase,
                    isExpanded = phase == HomeFeedbackPhase.Completed,
                    onStart = {},
                    onToggleExpanded = {},
                    modifier = Modifier.fillMaxWidth(),
                    result = previewFeedbackResult,
                )
            }
        }
    }
}

private val previewFeedbackResult = AiReviewContent(
    overallScore = 8.7,
    criteria = listOf(
        AiReviewCriterion(key = "composition", label = "구도", score = 8.7),
        AiReviewCriterion(key = "light", label = "빛", score = 8.2),
    ),
    distinctivePerspective = true,
    feedback = AiReviewFeedback(
        summary = "건물의 크기와 높이 차이가 잘 비교되는 구도예요",
        strength = "아래쪽 계단과 겹겹이 쌓인 집들이 한 화면에 같이 담겼어요.",
        weakness = "맨 아래 담장이 골목의 흐름을 조금 끊어요.",
        suggestion = "계단 입구는 남기면서 담장이 덜 나오게 담아보세요.",
    ),
)
