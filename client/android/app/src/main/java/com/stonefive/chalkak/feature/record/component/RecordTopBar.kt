package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

@Composable
fun RecordTopBar(
    month: YearMonth,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    onSaveClick: () -> Unit = {},
    showSaveLink: Boolean = true,
) {
    Row(
        modifier = modifier
            .padding(
                top = 20.dp,
                start = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_left,
            contentDescription = "이전 달",
            enabled = canGoPrevious,
            onClick = onPreviousMonthClick,
        )
        Text(
            text = month.format(MonthFormatter),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
        )
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_right,
            contentDescription = "다음 달",
            enabled = canGoNext,
            onClick = onNextMonthClick,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showSaveLink) {
            Text(
                text = "이미지로 저장",
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.footnote,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onSaveClick),
            )
        }
    }
}

@Composable
private fun CalendarArrowButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ChalkakTheme.colors.iconSecondary.copy(
                alpha = if (enabled) 1f else 0.35f,
            ),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun RecordTopBarPreview() {
    ChalkakTheme {
        RecordTopBar(
            month = YearMonth.of(2026, 8),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
