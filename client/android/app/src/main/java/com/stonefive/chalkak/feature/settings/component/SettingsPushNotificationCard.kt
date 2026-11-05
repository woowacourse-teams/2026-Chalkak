package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.component.toggle.ChalkakSwitch
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.NotificationPushSettings

@Composable
fun SettingsPushNotificationCard(
    settings: NotificationPushSettings?,
    isTopicSaving: Boolean,
    isModerationSaving: Boolean,
    onTopicPushChanged: (Boolean) -> Unit,
    onModerationPushChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onAiReviewPushChanged: (Boolean) -> Unit = {},
    isAiReviewSaving: Boolean = false,
) {
    SettingsCard(modifier = modifier) {
        SettingsPushToggleRow(
            text = "주제 알림",
            checked = settings?.topicPushEnabled == true,
            enabled = settings != null && !isTopicSaving,
            onCheckedChange = onTopicPushChanged,
        )
        SettingsDivider(modifier = Modifier.fillMaxWidth())
        SettingsPushToggleRow(
            text = "승인·반려 알림",
            checked = settings?.moderationPushEnabled == true,
            enabled = settings != null && !isModerationSaving,
            onCheckedChange = onModerationPushChanged,
        )
        SettingsDivider(modifier = Modifier.fillMaxWidth())
        SettingsPushToggleRow(
            text = "AI 피드백 알림",
            checked = settings?.aiReviewPushEnabled == true,
            enabled = settings != null && !isAiReviewSaving,
            onCheckedChange = onAiReviewPushChanged,
        )
    }
}

@Composable
private fun SettingsPushToggleRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        text = text,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        trailingContent = {
            ChalkakSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPushNotificationCardPreview() {
    ChalkakTheme {
        SettingsPushNotificationCard(
            settings = NotificationPushSettings(topicPushEnabled = true, moderationPushEnabled = true),
            isTopicSaving = false,
            isModerationSaving = false,
            onTopicPushChanged = {},
            onModerationPushChanged = {},
        )
    }
}
