package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FeedTopic(
    dateLabel: String,
    topic: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                end = ChalkakTheme.spacing.screenHorizontal,
                top = 16.dp,
                bottom = 40.dp,
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
            style = ChalkakTheme.typography.display,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedTopicPreview() {
    ChalkakTheme {
        FeedTopic(
            dateLabel = "8월 3일의 주제",
            topic = "하늘하늘하늘",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
