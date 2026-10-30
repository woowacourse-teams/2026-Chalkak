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
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.AppConfigEntry
import com.stonefive.chalkak.domain.model.AppConfigFetchStatus
import com.stonefive.chalkak.domain.model.AppConfigSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeveloperToolsRemoteConfigCard(
    remoteConfig: AppConfigDiagnostics,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ChalkakTheme.shapes.large,
        color = ChalkakTheme.colors.surfaceElevated,
    ) {
        Column(
            modifier = Modifier.padding(ChalkakTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.developer_tools_remote_config),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
            )
            remoteConfig.entries.forEach { entry ->
                RemoteConfigValue(
                    label = entry.key,
                    value = "${entry.value} (${entry.source.label()})",
                )
            }
            RemoteConfigValue(
                label = stringResource(R.string.developer_tools_remote_config_last_fetched_at),
                value = remoteConfig.lastFetchedAtMillis?.let(::formatFetchTime)
                    ?: stringResource(R.string.developer_tools_remote_config_never_fetched),
            )
            RemoteConfigValue(
                label = stringResource(R.string.developer_tools_remote_config_last_fetch_status),
                value = remoteConfig.lastFetchStatus.label(),
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_remote_config_refresh),
                onClick = onRefreshClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRefreshing,
            )
        }
    }
}

@Composable
private fun RemoteConfigValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs)) {
        Text(text = label, color = ChalkakTheme.colors.textMuted, style = ChalkakTheme.typography.subheadline)
        Text(text = value, color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.callout)
    }
}

@Composable
private fun AppConfigSource.label(): String = when (this) {
    AppConfigSource.Default -> stringResource(R.string.developer_tools_remote_config_source_default)
    AppConfigSource.Remote -> stringResource(R.string.developer_tools_remote_config_source_remote)
}

@Composable
private fun AppConfigFetchStatus.label(): String = when (this) {
    AppConfigFetchStatus.NotFetchedYet -> stringResource(R.string.developer_tools_remote_config_never_fetched)
    AppConfigFetchStatus.Success -> stringResource(R.string.developer_tools_remote_config_status_success)
    AppConfigFetchStatus.Failure -> stringResource(R.string.developer_tools_remote_config_status_failure)
    AppConfigFetchStatus.Throttled -> stringResource(R.string.developer_tools_remote_config_status_throttled)
}

private val fetchTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatFetchTime(millis: Long): String =
    fetchTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Preview(showBackground = true)
@Composable
private fun DeveloperToolsRemoteConfigCardPreview() {
    ChalkakTheme {
        DeveloperToolsRemoteConfigCard(
            remoteConfig = AppConfigDiagnostics(
                entries = listOf(
                    AppConfigEntry("ai_review_enabled", "true", AppConfigSource.Default),
                    AppConfigEntry("photo_edit_enabled", "false", AppConfigSource.Remote),
                ),
                lastFetchStatus = AppConfigFetchStatus.Success,
                lastFetchedAtMillis = 1_793_000_000_000L,
            ),
            isRefreshing = false,
            onRefreshClick = {},
        )
    }
}
