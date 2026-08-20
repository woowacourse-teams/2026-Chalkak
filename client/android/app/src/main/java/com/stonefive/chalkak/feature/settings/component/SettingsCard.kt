package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

internal val SettingsShape = RoundedCornerShape(16.dp)

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = SettingsShape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            ).clip(SettingsShape)
            .background(ChalkakTheme.colors.surfaceElevated),
        content = { content() },
    )
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsCardPreview() {
    ChalkakTheme {
        SettingsCard(
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "설정 카드",
                modifier = Modifier.padding(20.dp),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.callout,
            )
        }
    }
}
