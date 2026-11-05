package com.stonefive.chalkak.feature.feedback.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** 말풍선이 누구의 글인지. 좌우 위치·색이 정해진다. */
enum class FeedbackChatBubbleSide {
    /** 내 문의. 오른쪽, 진한 바탕(`actionPrimary`), 시각은 바깥 오른쪽 아래. */
    Mine,

    /** 운영팀 답변. 왼쪽, 밝은 회색 바탕(`calendarCell`), 시각은 바깥 왼쪽 아래. */
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
    val horizontalAlignment = if (isMine) Alignment.End else Alignment.Start

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
            SelectionContainer(
                modifier = Modifier
                    .background(
                        color = if (isMine) ChalkakTheme.colors.actionPrimary else ChalkakTheme.colors.calendarCell,
                        shape = RoundedCornerShape(BubbleCornerRadius),
                    ).padding(
                        horizontal = ChalkakTheme.spacing.lg,
                        vertical = ChalkakTheme.spacing.md,
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
            style = ChalkakTheme.typography.caption,
            color = ChalkakTheme.colors.textInactive,
        )
    }
}

/** 말풍선 최대 폭. 받은 폭에 대한 비율(시안 82%). */
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.82f

/** 말풍선 몸통의 모서리 반경. */
private val BubbleCornerRadius = 18.dp

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
