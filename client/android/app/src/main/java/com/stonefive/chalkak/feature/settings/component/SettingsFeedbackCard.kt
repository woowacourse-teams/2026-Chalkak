package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SettingsFeedbackCard(
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "피드백 보내기",
            onClick = onFeedbackClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsFeedbackCardPreview() {
    ChalkakTheme {
        SettingsFeedbackCard(
            onFeedbackClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
