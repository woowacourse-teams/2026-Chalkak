package com.stonefive.chalkak.feature.home.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/**
 * AI 피드백을 시작하기 전에 바텀 시트 안에 보여 주는 안내 내용이다.
 * 소요 시간·백그라운드 진행·완료 알림을 알려 주고, "분석 시작하기"를 누르면 [onStart]를 호출한다.
 * 바텀 시트 자체(열기·닫기)는 호출한 화면이 관리한다.
 */
@Composable
fun HomeFeedbackStartSheet(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
    ) {
        Text(
            text = "AI 피드백을 시작할까요?",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title3,
        )
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
        ChalkakButton(
            text = "분석 시작하기",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
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
            text = text,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val FeedbackStartNoteCircleSize = 36.dp
private val FeedbackStartNoteIconSize = 18.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeFeedbackStartSheetPreview() {
    ChalkakTheme {
        HomeFeedbackStartSheet(
            onStart = {},
            modifier = Modifier
                .background(ChalkakTheme.colors.surfaceElevated)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}
