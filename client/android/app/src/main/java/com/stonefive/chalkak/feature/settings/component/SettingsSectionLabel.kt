package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
internal fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = ChalkakTheme.colors.textMuted,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsSectionLabelPreview() {
    ChalkakTheme {
        SettingsSectionLabel(
            text = "정보 및 약관",
            modifier = Modifier.padding(16.dp),
        )
    }
}
