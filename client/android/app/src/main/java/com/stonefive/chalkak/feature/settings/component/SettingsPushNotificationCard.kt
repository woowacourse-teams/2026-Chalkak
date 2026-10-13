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
    onTopicPushChanged: (Boolean) -> Unit,
    onModerationPushChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = settings != null && !isSaving
    SettingsCard(modifier = modifier) {
        SettingsPushToggleRow(
            text = "주제 알림",
            checked = settings?.topicPushEnabled == true,
            enabled = enabled,
            onCheckedChange = onTopicPushChanged,
        )
        SettingsDivider(modifier = Modifier.fillMaxWidth())
        SettingsPushToggleRow(
            text = "승인·반려 알림",
            checked = settings?.moderationPushEnabled == true,
            enabled = enabled,
            onCheckedChange = onModerationPushChanged,
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
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
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
}

@Preview(showBackground = true)
@Composable
private fun SettingsPushNotificationCardPreview() {
    ChalkakTheme {
        SettingsPushNotificationCard(
            settings = NotificationPushSettings(topicPushEnabled = true, moderationPushEnabled = true),
            isSaving = false,
            onTopicPushChanged = {},
            onModerationPushChanged = {},
        )
    }
}

private const val NOTIFICATION_SWITCH_SCALE = 0.75f
