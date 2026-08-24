package com.stonefive.chalkak.feature.display.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun DisplayTopBar(
    selectedDate: LocalDate?,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(
                top = 20.dp,
                start = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayArrowButton(
            iconRes = R.drawable.ic_display_arrow_left,
            contentDescription = "이전 날짜",
            enabled = canGoPrevious,
            onClick = onPreviousClick,
        )

        Text(
            text = selectedDate?.format(DateFormatter).orEmpty(),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title2.copy(
                fontWeight = FontWeight.Normal,
            ),
        )

        DisplayArrowButton(
            iconRes = R.drawable.ic_display_arrow_right,
            contentDescription = "다음 날짜",
            enabled = canGoNext,
            onClick = onNextClick,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DisplayArrowButton(
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

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DisplayTopBarPreview() {
    ChalkakTheme {
        DisplayTopBar(
            selectedDate = LocalDate.of(2026, 8, 4),
            canGoPrevious = true,
            canGoNext = true,
            onPreviousClick = {},
            onNextClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
