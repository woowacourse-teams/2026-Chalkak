package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.NotificationPushSettings

@Composable
fun SettingsPushNotificationCard(
    settings: NotificationPushSettings?,
    isLoading: Boolean,
    isSaving: Boolean,
    deviceNotificationsEnabled: Boolean,
    onTopicPushChanged: (Boolean) -> Unit,
    onModerationPushChanged: (Boolean) -> Unit,
    onOpenDeviceNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        if (settings == null) {
            SettingsRow(
                text = if (isLoading) "푸시 알림 설정을 불러오는 중이에요." else "푸시 알림 설정을 불러올 수 없어요.",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SettingsRow(
                text = "오늘의 주제 알림",
                enabled = !isSaving,
                onClick = { onTopicPushChanged(!settings.topicPushEnabled) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Switch(
                        checked = settings.topicPushEnabled,
                        onCheckedChange = onTopicPushChanged,
                        enabled = !isSaving,
                    )
                },
            )
            SettingsDivider(modifier = Modifier.fillMaxWidth())
            SettingsRow(
                text = "게시물 승인·반려 결과",
                enabled = !isSaving,
                onClick = { onModerationPushChanged(!settings.moderationPushEnabled) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Switch(
                        checked = settings.moderationPushEnabled,
                        onCheckedChange = onModerationPushChanged,
                        enabled = !isSaving,
                    )
                },
            )
        }

        Column(modifier = Modifier.padding(horizontal = ChalkakTheme.spacing.lg)) {
            Text(
                text = "계정의 모든 로그인 기기에 적용돼요.",
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.caption,
            )
            Text(
                text = if (deviceNotificationsEnabled) {
                    "이 기기의 알림 권한이 허용돼 있어요."
                } else {
                    "이 기기의 Android 알림 권한이 꺼져 있어요."
                },
                modifier = Modifier.padding(top = ChalkakTheme.spacing.xs),
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.caption,
            )
            if (!deviceNotificationsEnabled) {
                SettingsRow(
                    text = "이 기기 알림 켜기",
                    onClick = onOpenDeviceNotificationSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ChalkakTheme.spacing.xs),
                )
            }
        }
    }
}
