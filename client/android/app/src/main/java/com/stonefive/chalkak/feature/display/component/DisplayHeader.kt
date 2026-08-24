package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate

private val HeaderDivider = Color(0xFFE8E6E1)

@Composable
fun DisplayHeader(
    selectedDate: LocalDate?,
    topic: String,
    isArchiveDate: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .headerBottomDivider()
            .padding(
                end = ChalkakTheme.spacing.screenHorizontal,
                bottom = 24.dp,
            ),
    ) {
        DisplayTopBar(
            selectedDate = selectedDate,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            modifier = Modifier.fillMaxWidth(),
        )
        DisplayTopicHeader(
            topic = topic,
            isArchiveDate = isArchiveDate,
        )
    }
}

private fun Modifier.headerBottomDivider(): Modifier = drawBehind {
    val strokeWidth = 0.5.dp.toPx()
    drawLine(
        color = HeaderDivider,
        start = Offset(0f, size.height - strokeWidth / 2),
        end = Offset(size.width, size.height - strokeWidth / 2),
        strokeWidth = strokeWidth,
    )
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DisplayHeaderPreview() {
    ChalkakTheme {
        DisplayHeader(
            selectedDate = LocalDate.of(2026, 8, 4),
            topic = "다리",
            isArchiveDate = true,
            canGoPrevious = true,
            canGoNext = true,
            onPreviousClick = {},
            onNextClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
