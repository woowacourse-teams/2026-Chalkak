package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val Weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
private val CalendarHorizontalPadding = 20.dp

@Composable
fun RecordTopBar(
    month: YearMonth,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    onSaveClick: () -> Unit = {},
    showSaveLink: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = 20.dp,
                end = ChalkakTheme.spacing.screenHorizontal,
            ).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_left,
            contentDescription = "이전 달",
            enabled = canGoPrevious,
            onClick = onPreviousMonthClick,
        )
        Box(
            modifier = Modifier.width(104.dp),
        ) {
            Text(
                text = month.format(MonthFormatter),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_right,
            contentDescription = "다음 달",
            enabled = canGoNext,
            onClick = onNextMonthClick,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showSaveLink) {
            RecordSaveLink(
                onClick = onSaveClick,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
fun RecordSaveLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "이미지로 저장",
        color = ChalkakTheme.colors.textMuted,
        style = ChalkakTheme.typography.footnote,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun RecordWeekdayHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CalendarHorizontalPadding),
    ) {
        Weekdays.forEach { weekday ->
            Text(
                text = weekday,
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
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

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordTopBarPreview() {
    ChalkakTheme {
        RecordTopBar(
            month = YearMonth.of(2026, 8),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordWeekdayHeaderPreview() {
    ChalkakTheme {
        RecordWeekdayHeader()
    }
}
