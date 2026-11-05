package com.stonefive.chalkak.feature.feedback.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feedback.FeedbackListItemUiState

/** 고객센터 목록의 한 줄. 문의 내용 2줄 말줄임과 접수 시각. 폭과 바깥 여백은 부모가 정한다. */
@Composable
fun FeedbackListItem(
    item: FeedbackListItemUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = ChalkakTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.sm),
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
}

private const val CONTENT_MAX_LINES = 2

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun FeedbackListItemPreview() {
    ChalkakTheme {
        FeedbackListItem(
            item = FeedbackListItemUiState(
                id = "1",
                content = "사진 업로드 후 화면이 멈춰요. 업로드 버튼을 누르면 한참 동안 아무 반응이 없다가 앱이 꺼져요.",
                timeText = "11월 3일 15:00",
            ),
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
