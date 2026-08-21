package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
internal fun SettingsRow(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = ChalkakTheme.colors.textPrimary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ).padding(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = textColor,
            style = ChalkakTheme.typography.callout,
        )

        Spacer(modifier = Modifier.weight(1f))

        trailingContent()
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(ChalkakTheme.colors.border, RectangleShape),
    )
}

@Composable
internal fun SettingsArrowIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_display_arrow_right),
        contentDescription = null,
        tint = ChalkakTheme.colors.textMuted,
        modifier = modifier.size(24.dp),
    )
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsRowPreview() {
    ChalkakTheme {
        SettingsCard(
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        ) {
            SettingsRow(
                text = "개인정보처리방침",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                trailingContent = { SettingsArrowIcon() },
            )
            SettingsDivider(modifier = Modifier.fillMaxWidth())
            SettingsRow(
                text = "버전정보",
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Text(
                        text = "1.0",
                        color = ChalkakTheme.colors.textMuted,
                        style = ChalkakTheme.typography.callout,
                    )
                },
            )
        }
    }
}
