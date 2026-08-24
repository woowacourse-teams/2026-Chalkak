package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderDivider = Color(0xFFE8E6E1)
private val ArchiveDescription = Color(0xFF8C8479)
private val DateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun DisplayDateHeader(
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
                top = 20.dp,
                end = ChalkakTheme.spacing.screenHorizontal,
                bottom = 24.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderArrowButton(
                iconRes = R.drawable.ic_display_arrow_left,
                contentDescription = "이전 날짜",
                enabled = canGoPrevious,
                onClick = onPreviousClick,
            )

            Text(
                text = selectedDate?.format(DateFormatter).orEmpty(),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
            )

            HeaderArrowButton(
                iconRes = R.drawable.ic_display_arrow_right,
                contentDescription = "다음 날짜",
                enabled = canGoNext,
                onClick = onNextClick,
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = topic,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2,
            modifier = Modifier.padding(start = ChalkakTheme.spacing.screenHorizontal),
        )

        Text(
            text = if (isArchiveDate) {
                "가장 사람들이 좋아했던 사진들이에요"
            } else {
                "같은 주제에서 다른 시선을 느껴보세요"
            },
            color = ArchiveDescription,
            style = ChalkakTheme.typography.subheadline,
            modifier = Modifier.padding(
                start = 19.dp,
                top = 12.dp,
            ),
        )
    }
}

@Composable
private fun HeaderArrowButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ChalkakTheme.colors.iconPrimary.copy(
                alpha = if (enabled) 1f else 0.35f,
            ),
            modifier = Modifier.size(24.dp),
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

@Preview(name = "과거 날짜", showBackground = true, widthDp = 390)
@Composable
private fun ArchiveDisplayDateHeaderPreview() {
    ChalkakTheme {
        DisplayDateHeader(
            selectedDate = LocalDate.of(2026, 8, 4),
            topic = "다리",
            isArchiveDate = true,
            canGoPrevious = true,
            canGoNext = true,
            onPreviousClick = {},
            onNextClick = {},
        )
    }
}

@Preview(name = "최신 날짜", showBackground = true, widthDp = 390)
@Composable
private fun LatestDisplayDateHeaderPreview() {
    ChalkakTheme {
        DisplayDateHeader(
            selectedDate = LocalDate.of(2026, 8, 5),
            topic = "바다",
            isArchiveDate = false,
            canGoPrevious = true,
            canGoNext = false,
            onPreviousClick = {},
            onNextClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderArrowButtonPreview() {
    ChalkakTheme {
        HeaderArrowButton(
            iconRes = R.drawable.ic_display_arrow_left,
            contentDescription = "이전 날짜",
            enabled = true,
            onClick = {},
        )
    }
}
