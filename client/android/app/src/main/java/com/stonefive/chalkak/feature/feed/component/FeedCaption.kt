package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FeedCaption(
    title: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title?.takeIf { it.isNotBlank() } ?: "무제",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.subheadline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedCaptionPreview() {
    ChalkakTheme {
        FeedCaption(title = "안녕하세요 감사합니다.")
    }
}
