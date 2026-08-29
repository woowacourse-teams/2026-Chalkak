package com.stonefive.chalkak.core.designsystem.component.button

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ChalkakTheme.shapes.pill,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = ChalkakTheme.colors.actionPrimary,
            contentColor = ChalkakTheme.colors.onActionPrimary,
            disabledContainerColor = ChalkakTheme.colors.actionPrimary
                .copy(alpha = 0.12f),
            disabledContentColor = ChalkakTheme.colors.textMuted,
        ),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ChalkakFilledIconButtonPreview() {
    ChalkakTheme {
        ChalkakFilledIconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "새로고침",
            )
        }
    }
}
