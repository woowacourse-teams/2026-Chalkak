package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
fun RecordCalendarHeader(
    month: YearMonth,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_left,
            contentDescription = "이전 달",
            onClick = onPreviousMonthClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
        )
        Text(
            text = month.format(MonthFormatter),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )
        CalendarArrowButton(
            iconRes = R.drawable.ic_display_arrow_right,
            contentDescription = "다음 달",
            onClick = onNextMonthClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
        )
    }
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
                style = ChalkakTheme.typography.subheadline,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ChalkakTheme.colors.iconSecondary,
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordCalendarHeaderPreview() {
    ChalkakTheme {
        RecordCalendarHeader(
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
