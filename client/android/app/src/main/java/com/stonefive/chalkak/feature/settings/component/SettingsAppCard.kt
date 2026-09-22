package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SettingsAppCard(
    showSignature: Boolean,
    signatureUrl: String?,
    onReminderClick: () -> Unit,
    onChangeSignatureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "알림 설정",
            onClick = onReminderClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )

        if (showSignature) {
            SettingsDivider(modifier = Modifier.fillMaxWidth())

            SettingsRow(
                text = "사인 재설정",
                onClick = onChangeSignatureClick,
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Text(
                        text = "변경하기",
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.callout,
                        textDecoration = TextDecoration.Underline,
                    )
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .height(112.dp)
                    .clip(SettingsShape)
                    .border(
                        1.dp,
                        ChalkakTheme.colors.textOnImage
                            .copy(alpha = 0.28f),
                        SettingsShape,
                    ).background(ChalkakTheme.colors.actionPrimary),
                contentAlignment = Alignment.Center,
            ) {
                if (signatureUrl != null) {
                    ChalkakImage(
                        model = signatureUrl,
                        contentDescription = "현재 사인",
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(48.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsAppCardPreview() {
    ChalkakTheme {
        SettingsAppCard(
            showSignature = true,
            signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
            onReminderClick = {},
            onChangeSignatureClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
