package com.stonefive.chalkak.core.designsystem.component.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledContainerColor: Color = Color.Unspecified,
    disabledContentColor: Color = Color.Unspecified,
) {
    val resolvedDisabledContainerColor = if (disabledContainerColor == Color.Unspecified) {
        ChalkakTheme.colors.actionPrimary
            .copy(alpha = 0.12f)
    } else {
        disabledContainerColor
    }
    val resolvedDisabledContentColor = if (disabledContentColor == Color.Unspecified) {
        ChalkakTheme.colors.textPrimary
    } else {
        disabledContentColor
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ChalkakTheme.shapes.button,
        contentPadding = PaddingValues(
            horizontal = 24.dp,
            vertical = 17.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChalkakTheme.colors.actionPrimary,
            contentColor = ChalkakTheme.colors.onActionPrimary,
            disabledContainerColor = resolvedDisabledContainerColor,
            disabledContentColor = resolvedDisabledContentColor,
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
private fun ChalkakButtonEnabledPreview() {
    ChalkakTheme {
        ChalkakButton(
            text = "전시하기",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakButtonDisabledPreview() {
    ChalkakTheme {
        ChalkakButton(
            text = "전시하기",
            onClick = {},
            enabled = false,
        )
    }
}
