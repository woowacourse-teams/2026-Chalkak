package com.stonefive.chalkak.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.feature.aireview.component.AiReviewAudioWaveIcon
import com.stonefive.chalkak.feature.aireview.component.AiReviewResultContent
import com.stonefive.chalkak.feature.home.HomeFeedbackPhase

/**
 * 홈의 오늘 사진 아래에 놓이는 AI 피드백 카드다. [phase]에 따라 한 줄 헤더의 제목과 동작이 바뀐다.
 *
 * - [HomeFeedbackPhase.Ready]: 헤더를 누르면 [onStart]를 호출한다. 호출한 쪽이 시작 안내 시트를 연다.
 * - [HomeFeedbackPhase.Processing]: 헤더를 누를 수 없고, 오른쪽 음성 파형 아이콘만 움직인다.
 * - [HomeFeedbackPhase.Completed]: 헤더를 누르면 [onToggleExpanded]를 호출한다. [isExpanded]가 true면 결과를 펼친다.
 * - [HomeFeedbackPhase.Failed]: 실패 안내를 보여 준다. [canRequestAgain]이 true면 "재요청"을 함께 보여 주고
 *   헤더를 누르면 [onStart]를 호출한다. 호출한 쪽이 시작 안내 없이 바로 다시 요청한다. false면 누를 수 없다.
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
    canRequestAgain: Boolean = false,
) {
    if (phase == HomeFeedbackPhase.Hidden) return

    val showsDetail = phase == HomeFeedbackPhase.Completed && isExpanded

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
                canRequestAgain = canRequestAgain,
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
    }
}

@Composable
private fun FeedbackHeader(
    phase: HomeFeedbackPhase,
    isExpanded: Boolean,
    canRequestAgain: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (phase) {
        HomeFeedbackPhase.Hidden -> ""
        HomeFeedbackPhase.Ready -> "이 사진, AI에게 피드백 받아보기"
        HomeFeedbackPhase.Processing -> "AI 피드백이 진행중이에요"
        HomeFeedbackPhase.Completed -> "AI 피드백이 완료되었어요"
        HomeFeedbackPhase.Failed -> "AI 피드백을 생성하지 못했어요"
    }
    val showsRequestAgain = phase == HomeFeedbackPhase.Failed && canRequestAgain
    val enabled = when (phase) {
        HomeFeedbackPhase.Processing -> false
        HomeFeedbackPhase.Failed -> canRequestAgain
        else -> true
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = modifier
            .heightIn(min = FeedbackHeaderMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).semantics {
                contentDescription = if (showsRequestAgain) "$title, 재요청" else title
                if (phase == HomeFeedbackPhase.Completed) {
                    stateDescription = if (isExpanded) "펼쳐짐" else "접힘"
                }
            }.alpha(if (isPressed) FEEDBACK_HEADER_PRESSED_ALPHA else 1f)
            .padding(
                start = ChalkakTheme.spacing.xl,
                end = ChalkakTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
    ) {
        Text(
            text = title,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.callout,
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
            val chevronRotation by animateFloatAsState(
                targetValue = if (isExpanded) CHEVRON_EXPANDED_ROTATION else 0f,
                label = "feedback-chevron-rotation",
            )
            Box(
                modifier = Modifier.size(FeedbackHeaderIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = ChalkakTheme.colors.iconSecondary,
                    modifier = Modifier
                        .size(FeedbackChevronSize)
                        .rotate(chevronRotation),
                )
            }
        } else {
            if (showsRequestAgain) {
                Text(
                    text = "재요청",
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.footnote,
                )
            }
            Box(
                modifier = Modifier
                    .size(FeedbackWaveCircleSize)
                    .background(ChalkakTheme.colors.actionPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AiReviewAudioWaveIcon(
                    isAnimating = phase == HomeFeedbackPhase.Processing,
                    color = ChalkakTheme.colors.onActionPrimary,
                )
            }
        }
    }
}

@Composable
private fun FeedbackDetail(
    result: AiReviewContent?,
    modifier: Modifier = Modifier,
) {
    result?.let {
        AiReviewResultContent(result = it, modifier = modifier)
    }
}

private const val FEEDBACK_CARD_SHADOW_ALPHA = 0.08f
private const val FEEDBACK_HEADER_PRESSED_ALPHA = 0.8f
private const val CHEVRON_EXPANDED_ROTATION = 180f
private val FeedbackCardShadowElevation = 12.dp
private val FeedbackCardBorderWidth = 1.dp
private val FeedbackHeaderMinHeight = 64.dp
private val FeedbackHeaderIconSize = 44.dp
private val FeedbackChevronSize = 16.dp
private val FeedbackWaveCircleSize = 32.dp

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
