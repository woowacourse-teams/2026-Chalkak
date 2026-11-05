package com.stonefive.chalkak.feature.feedback.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** 말풍선이 누구의 글인지. 좌우 위치·색·말꼬리 방향이 정해진다. */
enum class FeedbackChatBubbleSide {
    /** 내 문의. 왼쪽, 진한 바탕(`actionPrimary`), 말꼬리 왼쪽 위, 시각은 바깥 왼쪽 아래. */
    Mine,

    /** 운영팀 답변. 오른쪽, 밝은 회색 바탕(`calendarCell`), 말꼬리 오른쪽 위, 시각은 바깥 오른쪽 아래. */
    Answer,
}

/**
 * 문의 상세의 메신저형 말풍선 하나와 그 아래 시각.
 *
 * 말풍선은 받은 폭의 약 82%까지 넓어지고, 긴 글은 그 안에서 줄바꿈한다. 글은 길게 눌러 복사할 수 있다.
 * 부모가 폭(보통 `fillMaxWidth`)과 바깥 여백을 정하고, 이 컴포넌트는 그 안에서 [side] 쪽으로 붙는다.
 *
 * 예: `FeedbackChatBubble(text = "사진이 안 보여요", timeText = "09:30", side = FeedbackChatBubbleSide.Mine)`
 */
@Composable
fun FeedbackChatBubble(
    text: String,
    timeText: String,
    side: FeedbackChatBubbleSide,
    modifier: Modifier = Modifier,
) {
    val isMine = side == FeedbackChatBubbleSide.Mine
    val horizontalAlignment = if (isMine) Alignment.Start else Alignment.End

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(BUBBLE_MAX_WIDTH_FRACTION)
                .wrapContentWidth(horizontalAlignment),
        ) {
            // 말꼬리는 말풍선 몸통 바깥에 그려지므로, 말꼬리 쪽 안쪽 여백에 말꼬리 폭을 더한다.
            val tailPadding = BubbleTailWidth + ChalkakTheme.spacing.lg
            SelectionContainer(
                modifier = Modifier
                    .background(
                        color = if (isMine) ChalkakTheme.colors.actionPrimary else ChalkakTheme.colors.calendarCell,
                        shape = FeedbackChatBubbleShape(side),
                    ).padding(
                        start = if (isMine) tailPadding else ChalkakTheme.spacing.lg,
                        end = if (isMine) ChalkakTheme.spacing.lg else tailPadding,
                        top = ChalkakTheme.spacing.md,
                        bottom = ChalkakTheme.spacing.md,
                    ),
            ) {
                Text(
                    text = text,
                    style = ChalkakTheme.typography.body,
                    color = if (isMine) ChalkakTheme.colors.onActionPrimary else ChalkakTheme.colors.textPrimary,
                )
            }
        }
        Text(
            text = timeText,
            // 시각을 말꼬리 끝이 아니라 말풍선 몸통 가장자리에 맞춘다.
            modifier = Modifier.padding(
                start = if (isMine) BubbleTailWidth else ChalkakTheme.spacing.none,
                end = if (isMine) ChalkakTheme.spacing.none else BubbleTailWidth,
            ),
            style = ChalkakTheme.typography.caption,
            color = ChalkakTheme.colors.textInactive,
        )
    }
}

/**
 * 말풍선 모양. 몸통은 둥근 사각형이고, 말꼬리 쪽 위 모서리만 작게 둥글린 뒤
 * 그 모서리에서 바깥으로 삼각형 말꼬리를 붙인다. 말꼬리 폭([BubbleTailWidth])만큼 몸통이 안쪽으로 들어간다.
 */
private class FeedbackChatBubbleShape(private val side: FeedbackChatBubbleSide) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = with(density) {
        // RTL에서는 좌우를 뒤집어 "시작 쪽"에 말꼬리를 둔다.
        val tailOnLeft = (side == FeedbackChatBubbleSide.Mine) == (layoutDirection == LayoutDirection.Ltr)
        val tailWidth = BubbleTailWidth.toPx()
        val tailHeight = BubbleTailHeight.toPx()
        val cornerRadius = CornerRadius(BubbleCornerRadius.toPx())
        val tailCornerRadius = CornerRadius(BubbleTailCornerRadius.toPx())

        val bodyLeft = if (tailOnLeft) tailWidth else 0f
        val bodyRight = if (tailOnLeft) size.width else size.width - tailWidth
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = bodyLeft,
                    top = 0f,
                    right = bodyRight,
                    bottom = size.height,
                    topLeftCornerRadius = if (tailOnLeft) tailCornerRadius else cornerRadius,
                    topRightCornerRadius = if (tailOnLeft) cornerRadius else tailCornerRadius,
                    bottomRightCornerRadius = cornerRadius,
                    bottomLeftCornerRadius = cornerRadius,
                ),
            )
        }
        // 말꼬리: 바깥 끝(위) → 몸통 안쪽으로 작은 모서리만큼 들어간 점 → 그 아래로 말꼬리 높이만큼.
        val tailInnerX = if (tailOnLeft) bodyLeft + tailCornerRadius.x else bodyRight - tailCornerRadius.x
        val tail = Path().apply {
            moveTo(if (tailOnLeft) 0f else size.width, 0f)
            lineTo(tailInnerX, 0f)
            lineTo(tailInnerX, tailHeight)
            close()
        }
        Outline.Generic(Path().apply { op(body, tail, PathOperation.Union) })
    }
}

/** 말풍선 최대 폭. 받은 폭에 대한 비율(시안 82%). */
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.82f

/** 말풍선 몸통의 모서리 반경. */
private val BubbleCornerRadius = 18.dp

/** 말꼬리가 붙는 위 모서리의 작은 반경. */
private val BubbleTailCornerRadius = 4.dp

/** 몸통 바깥으로 나온 말꼬리 폭. */
private val BubbleTailWidth = 6.dp

/** 말꼬리 높이(위 모서리에서 아래로). */
private val BubbleTailHeight = 12.dp

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun FeedbackChatBubblePreview() {
    ChalkakTheme {
        Column(
            modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            FeedbackChatBubble(
                text = "전시한 사진이 피드에 안 보여요. 전시하기를 눌렀는데 피드에서 찾을 수가 없어요.",
                timeText = "09:30",
                side = FeedbackChatBubbleSide.Mine,
                modifier = Modifier.fillMaxWidth(),
            )
            FeedbackChatBubble(
                text = "안녕하세요. 알려 주신 문제를 확인했어요. 다음 업데이트에서 고칠게요.",
                timeText = "16:12",
                side = FeedbackChatBubbleSide.Answer,
                modifier = Modifier.fillMaxWidth(),
            )
            FeedbackChatBubble(
                text = "짧은 문의",
                timeText = "어제 18:20",
                side = FeedbackChatBubbleSide.Mine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
