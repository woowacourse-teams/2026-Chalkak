package com.stonefive.chalkak.feature.record.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val Weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun RecordWeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
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

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun RecordWeekdayHeaderPreview() {
    ChalkakTheme {
        RecordWeekdayHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
    }
}
