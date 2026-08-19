package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val FeedHorizontalPadding = 20.dp
private val FeedDivider = Color(0xFFB8B5AF)

@Composable
fun FeedCaption(
    title: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(57.dp)
            .feedTopDivider()
            .padding(horizontal = FeedHorizontalPadding),
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

private fun Modifier.feedTopDivider(): Modifier = drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    drawLine(
        color = FeedDivider,
        start = Offset(0f, strokeWidth / 2),
        end = Offset(size.width, strokeWidth / 2),
        strokeWidth = strokeWidth,
    )
}
