package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.aireview.component.AiReviewStartSheet

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
    AiReviewStartSheet(onStart = onStart, modifier = modifier)
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeFeedbackStartSheetPreview() {
    ChalkakTheme {
        HomeFeedbackStartSheet(
            onStart = {},
            modifier = Modifier
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}
