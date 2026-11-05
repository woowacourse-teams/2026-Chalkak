package com.stonefive.chalkak.feature.feedback.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feedback.FeedbackAnswerStatus
import com.stonefive.chalkak.feature.feedback.FeedbackListItemUiState

/**
 * 고객센터 목록의 한 줄. 왼쪽에 문의 내용 1줄 말줄임과 접수 시각, 오른쪽에 답변 상태 글자.
 * 상태 글자는 "답변 대기"(흐린 색) / "답변 완료"(굵게)이고, [FeedbackListItemUiState.answerStatus]가 null이면 없다.
 * 폭과 바깥 여백은 부모가 정한다.
 */
@Composable
fun FeedbackListItem(
    item: FeedbackListItemUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = ChalkakTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs),
        ) {
            Text(
                text = item.content,
                style = ChalkakTheme.typography.subheadline,
                color = ChalkakTheme.colors.textPrimary,
                maxLines = CONTENT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.timeText,
                style = ChalkakTheme.typography.caption,
                color = ChalkakTheme.colors.textInactive,
            )
        }
        item.answerStatus?.let { status -> FeedbackAnswerStatusText(status = status) }
    }
}

@Composable
private fun FeedbackAnswerStatusText(status: FeedbackAnswerStatus) {
    when (status) {
        FeedbackAnswerStatus.Answered -> Text(
            text = "답변 완료",
            style = ChalkakTheme.typography.caption,
            color = ChalkakTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        FeedbackAnswerStatus.Waiting -> Text(
            text = "답변 대기",
            style = ChalkakTheme.typography.caption,
            color = ChalkakTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

private const val CONTENT_MAX_LINES = 1

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun FeedbackListItemPreview() {
    ChalkakTheme {
        Column {
            listOf(
                FeedbackListItemUiState(
                    id = "1",
                    content = "전시한 사진이 피드에 안 보여요. 전시하기를 눌렀는데 피드에서 찾을 수가 없어요.",
                    timeText = "09:30",
                    answerStatus = FeedbackAnswerStatus.Waiting,
                ),
                FeedbackListItemUiState(
                    id = "2",
                    content = "사진 업로드 후 화면이 멈춰요. 업로드 버튼을 누르면 한참 동안 아무 반응이 없다가 앱이 꺼져요.",
                    timeText = "어제 14:20",
                    answerStatus = FeedbackAnswerStatus.Answered,
                ),
                FeedbackListItemUiState(
                    id = "3",
                    content = "답변 여부를 모르는 문의(상태 글자 없음)",
                    timeText = "11월 1일 09:12",
                ),
            ).forEach { item ->
                FeedbackListItem(
                    item = item,
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                )
            }
        }
    }
}
