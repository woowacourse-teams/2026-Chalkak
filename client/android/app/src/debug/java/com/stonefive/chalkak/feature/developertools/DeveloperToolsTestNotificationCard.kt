package com.stonefive.chalkak.feature.developertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun DeveloperToolsTestNotificationCard(
    onSendClick: (DeveloperToolsTestNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ChalkakTheme.shapes.large,
        color = ChalkakTheme.colors.surfaceElevated,
    ) {
        Column(
            modifier = Modifier.padding(ChalkakTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.developer_tools_test_notification),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
            )
            Text(
                text = stringResource(R.string.developer_tools_test_notification_description),
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.subheadline,
            )
            DeveloperToolsTestNotification.entries.forEach { notification ->
                ChalkakOutlinedButton(
                    text = stringResource(notification.labelRes),
                    onClick = { onSendClick(notification) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DeveloperToolsTestNotificationCardPreview() {
    ChalkakTheme {
        DeveloperToolsTestNotificationCard(onSendClick = {})
    }
}
