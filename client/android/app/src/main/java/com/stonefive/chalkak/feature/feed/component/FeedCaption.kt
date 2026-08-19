package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val FeedHorizontalPadding = 20.dp
private val FeedDivider = Color(0xFFB8B5AF)

@Composable
fun FeedCaption(
    title: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .feedTopDivider()
            .padding(
                start = FeedHorizontalPadding,
                top = 16.dp,
                end = FeedHorizontalPadding,
            ),
    ) {
        Text(
            text = title?.takeIf { it.isNotBlank() } ?: "무제",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
