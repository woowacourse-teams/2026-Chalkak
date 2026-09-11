package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SettingsInformationCard(
    versionName: String,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "개인정보처리방침",
            onClick = onPrivacyPolicyClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )

        SettingsDivider(modifier = Modifier.fillMaxWidth())

        SettingsRow(
            text = "이용약관",
            onClick = onTermsClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )

        SettingsDivider(modifier = Modifier.fillMaxWidth())

        SettingsRow(
            text = "버전정보",
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                Text(
                    text = versionName,
                    color = ChalkakTheme.colors.textMuted,
                    style = ChalkakTheme.typography.callout,
                )
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsInformationCardPreview() {
    ChalkakTheme {
        SettingsInformationCard(
            versionName = "1.0",
            onPrivacyPolicyClick = {},
            onTermsClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
