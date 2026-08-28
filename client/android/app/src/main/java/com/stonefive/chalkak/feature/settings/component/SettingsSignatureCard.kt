package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
fun SettingsSignatureCard(
    signatureUrl: String?,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "사인 재설정",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.callout,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "변경하기",
                modifier = Modifier
                    .clickable(onClick = onChangeClick)
                    .padding(vertical = 5.dp, horizontal = 10.dp),
                color = ChalkakTheme.colors.textSecondary,
                style = ChalkakTheme.typography.callout,
                textDecoration = TextDecoration.Underline,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)
                .height(112.dp)
                .clip(SettingsShape)
                .border(1.dp, ChalkakTheme.colors.border, SettingsShape)
                .background(ChalkakTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = ChalkakTheme.colors.error,
                    style = ChalkakTheme.typography.caption,
                )
            } else if (signatureUrl != null) {
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

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsSignatureCardPreview() {
    ChalkakTheme {
        SettingsSignatureCard(
            signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
            onChangeClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
