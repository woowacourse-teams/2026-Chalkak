package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val ArchiveDescription = Color(0xFF8C8479)

@Composable
fun DisplayTopicHeader(
    topic: String,
    isArchiveDate: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = topic,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2,
            modifier = Modifier.padding(start = ChalkakTheme.spacing.screenHorizontal),
        )

        if (isArchiveDate) {
            Text(
                text = "가장 사람들이 좋아했던 사진들이에요",
                color = ArchiveDescription,
                style = ChalkakTheme.typography.subheadline,
                modifier = Modifier.padding(
                    start = 19.dp,
                    top = 12.dp,
                ),
            )
        }
    }
}

@Preview(name = "과거 날짜", showBackground = true, widthDp = 390)
@Composable
private fun ArchiveDisplayTopicHeaderPreview() {
    ChalkakTheme {
        DisplayTopicHeader(
            topic = "다리",
            isArchiveDate = true,
        )
    }
}

@Preview(name = "최신 날짜", showBackground = true, widthDp = 390)
@Composable
private fun LatestDisplayTopicHeaderPreview() {
    ChalkakTheme {
        DisplayTopicHeader(
            topic = "바다",
            isArchiveDate = false,
        )
    }
}
