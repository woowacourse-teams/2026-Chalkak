package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

private val HomeDivider = Color(0xFFE8E6E1)

@Composable
fun HomeTopic(
    dateLabel: String,
    topic: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .homeBottomDivider()
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                top = ChalkakTheme.spacing.lg,
                end = ChalkakTheme.spacing.screenHorizontal,
                bottom = ChalkakTheme.spacing.xxl,
            ),
    ) {
        Text(
            text = dateLabel,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.subheadline,
        )
        Text(
            text = topic,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1
                .copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 10.dp),
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
            dateLabel = "8월 3일 · 오늘의 주제",
            topic = "하늘하늘하늘",
        )
    }
}
