package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeDivider = Color(0xFFE8E6E1)
private val HomeDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun HomeTopic(
    topicDate: LocalDate?,
    topic: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .homeBottomDivider()
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                end = ChalkakTheme.spacing.screenHorizontal,
                top = 16.dp,
                bottom = 20.dp,
            ),
    ) {
        Text(
            text = topicDate?.let { "${it.format(HomeDateFormatter)} · 오늘의 주제" }.orEmpty(),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = topic,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1
                .copy(fontWeight = FontWeight.Bold),
        )
    }
}

fun Modifier.homeBottomDivider(): Modifier = drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    drawLine(
        color = HomeDivider,
        start = Offset(0f, size.height - strokeWidth / 2),
        end = Offset(size.width, size.height - strokeWidth / 2),
        strokeWidth = strokeWidth,
    )
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeTopicPreview() {
    ChalkakTheme {
        HomeTopic(
            topicDate = LocalDate.of(2026, 8, 3),
            topic = "하늘하늘하늘",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
