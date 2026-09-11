package com.stonefive.chalkak.core.designsystem.component.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ChalkakTheme.shapes.button,
        border = BorderStroke(
            width = 1.dp,
            color = ChalkakTheme.colors.border,
        ),
        contentPadding = PaddingValues(
            horizontal = 24.dp,
            vertical = 17.dp,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ChalkakTheme.colors.textPrimary,
            disabledContentColor = ChalkakTheme.colors.textMuted,
        ),
    ) {
        Text(
            text = text,
            style = ChalkakTheme.typography.callout,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakOutlinedButtonPreview() {
    ChalkakTheme {
        ChalkakOutlinedButton(
            text = "다시 그리기",
            onClick = {},
        )
    }
}
