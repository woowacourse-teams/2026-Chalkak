package com.stonefive.chalkak.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar
import com.stonefive.chalkak.feature.settings.component.SettingsPushNotificationCard

@Composable
fun NotificationSettingsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    NotificationSettingsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onTopicPushChanged = { enabled ->
            viewModel.updateTopicPushEnabled(enabled)
            context.openDeviceNotificationSettingsIfBlocked(enabled)
        },
        onModerationPushChanged = { enabled ->
            viewModel.updateModerationPushEnabled(enabled)
            context.openDeviceNotificationSettingsIfBlocked(enabled)
        },
        onAiReviewPushChanged = { enabled ->
            viewModel.updateAiReviewPushEnabled(enabled)
            context.openDeviceNotificationSettingsIfBlocked(enabled)
        },
        onLikePushChanged = { enabled ->
            viewModel.updateLikePushEnabled(enabled)
            context.openDeviceNotificationSettingsIfBlocked(enabled)
        },
        modifier = modifier,
    )
}

@Composable
fun NotificationSettingsScreen(
    uiState: NotificationSettingsUiState,
    onBackClick: () -> Unit,
    onTopicPushChanged: (Boolean) -> Unit,
    onModerationPushChanged: (Boolean) -> Unit,
    onAiReviewPushChanged: (Boolean) -> Unit,
    onLikePushChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        NotificationTopBar(
            onBackClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
            title = "알림 설정",
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ChalkakTheme.colors.actionPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = ChalkakTheme.spacing.screenHorizontal,
                        vertical = ChalkakTheme.spacing.lg,
                    ),
            ) {
                SettingsPushNotificationCard(
                    settings = uiState.pushSettings,
                    isTopicSaving = uiState.isTopicPushSaving,
                    isModerationSaving = uiState.isModerationPushSaving,
                    isAiReviewSaving = uiState.isAiReviewPushSaving,
                    isLikeSaving = uiState.isLikePushSaving,
                    onTopicPushChanged = onTopicPushChanged,
                    onModerationPushChanged = onModerationPushChanged,
                    onAiReviewPushChanged = onAiReviewPushChanged,
                    onLikePushChanged = onLikePushChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Context.openDeviceNotificationSettingsIfBlocked(enabled: Boolean) {
    if (enabled && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
        openDeviceNotificationSettings()
    }
}

private fun Context.openDeviceNotificationSettings() {
    val appNotificationsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:$packageName"))
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        appNotificationsIntent
    } else {
        appDetailsIntent
    }
    startActivity(intent)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationSettingsScreenPreview() {
    ChalkakTheme {
        NotificationSettingsScreen(
            uiState = NotificationSettingsUiState(
                pushSettings = NotificationPushSettings(
                    topicPushEnabled = true,
                    moderationPushEnabled = true,
                    aiReviewPushEnabled = false,
                    likePushEnabled = true,
                ),
                isLoading = false,
            ),
            onBackClick = {},
            onTopicPushChanged = {},
            onModerationPushChanged = {},
            onAiReviewPushChanged = {},
            onLikePushChanged = {},
        )
    }
}
