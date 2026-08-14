package com.stonefive.chalkak.feature.signature.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
internal fun SignatureControlButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (enabled) {
        ChalkakTheme.colors.textPrimary
    } else {
        ChalkakTheme.colors.textMuted
    }

    Box(
        modifier = modifier
            .clip(ChalkakTheme.shapes.pill)
            .border(
                width = 1.dp,
                color = ChalkakTheme.colors.textPrimary
                    .copy(alpha = 0.12f),
                shape = ChalkakTheme.shapes.pill,
            ).clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = ChalkakTheme.typography.footnote,
        )
    }
}

@Preview(
    name = "Enabled",
    showBackground = true,
)
@Composable
private fun SignatureControlButtonEnabledPreview() {
    ChalkakTheme {
        SignatureControlButtonPreview(enabled = true)
    }
}

@Preview(
    name = "Disabled",
    showBackground = true,
)
@Composable
private fun SignatureControlButtonDisabledPreview() {
    ChalkakTheme {
        SignatureControlButtonPreview(enabled = false)
    }
}

@Composable
private fun SignatureControlButtonPreview(enabled: Boolean) {
    SignatureControlButton(
        text = "되돌리기",
        enabled = enabled,
        onClick = {},
        modifier = Modifier.padding(24.dp),
    )
}
