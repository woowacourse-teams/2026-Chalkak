package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SettingsNotificationEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "알림 설정",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsNotificationEntryCardPreview() {
    ChalkakTheme {
        SettingsNotificationEntryCard(
            onClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
