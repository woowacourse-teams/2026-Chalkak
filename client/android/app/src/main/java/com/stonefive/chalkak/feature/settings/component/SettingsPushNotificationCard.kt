package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import kotlin.math.roundToInt

@Composable
fun SettingsPushNotificationCard(
    settings: NotificationPushSettings?,
    isSaving: Boolean,
    onPushChanged: (Boolean) -> Unit,
    onDeviceNotificationSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = settings?.let { it.topicPushEnabled && it.moderationPushEnabled } == true
    val enabled = settings != null && !isSaving
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "알림 설정",
            enabled = enabled,
            onClick = { onPushChanged(!checked) },
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Switch(
                        checked = checked,
                        onCheckedChange = onPushChanged,
                        enabled = enabled,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val width = (placeable.width * NOTIFICATION_SWITCH_SCALE).roundToInt()
                                val height = (placeable.height * NOTIFICATION_SWITCH_SCALE).roundToInt()
                                layout(width, height) {
                                    placeable.placeRelative(
                                        x = (width - placeable.width) / 2,
                                        y = (height - placeable.height) / 2,
                                    )
                                }
                            }.scale(NOTIFICATION_SWITCH_SCALE),
                        colors = SwitchDefaults.colors(
                            uncheckedBorderColor = ChalkakTheme.colors.textMuted,
                            disabledUncheckedBorderColor = ChalkakTheme.colors.textMuted,
                        ),
                    )
                }
            },
        )

        SettingsDivider(modifier = Modifier.fillMaxWidth())

        SettingsRow(
            text = "기기 알림 설정",
            onClick = onDeviceNotificationSettingsClick,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { SettingsArrowIcon() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPushNotificationCardPreview() {
    ChalkakTheme {
        SettingsPushNotificationCard(
            settings = NotificationPushSettings(topicPushEnabled = true, moderationPushEnabled = true),
            isSaving = false,
            onPushChanged = {},
            onDeviceNotificationSettingsClick = {},
        )
    }
}

private const val NOTIFICATION_SWITCH_SCALE = 0.75f
