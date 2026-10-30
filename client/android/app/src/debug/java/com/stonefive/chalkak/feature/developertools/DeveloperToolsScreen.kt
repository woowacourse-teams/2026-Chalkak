package com.stonefive.chalkak.feature.developertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.AppConfigEntry
import com.stonefive.chalkak.domain.model.AppConfigFetchStatus
import com.stonefive.chalkak.domain.model.AppConfigSource

@Composable
fun DeveloperToolsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeveloperToolsViewModel = viewModel(factory = DeveloperToolsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeveloperToolsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshRemoteConfigClick = viewModel::refreshRemoteConfig,
        modifier = modifier,
    )
}

@Composable
fun DeveloperToolsScreen(
    uiState: DeveloperToolsUiState,
    onBackClick: () -> Unit,
    onRefreshRemoteConfigClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakTheme.colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = ChalkakTheme.spacing.screenHorizontal,
                    vertical = ChalkakTheme.spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.developer_tools_title),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.title2,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ChalkakTheme.shapes.large,
                color = ChalkakTheme.colors.surfaceElevated,
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier.padding(ChalkakTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xl),
                    ) {
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_version_name),
                            uiState.versionName,
                        )
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_version_code),
                            uiState.versionCode.toString(),
                        )
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_build_type),
                            uiState.buildType,
                        )
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_application_id),
                            uiState.applicationId,
                        )
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_server_environment),
                            uiState.serverEnvironment,
                        )
                        DeveloperToolsValue(
                            stringResource(R.string.developer_tools_server_url),
                            uiState.serverBaseUrl,
                        )
                    }
                }
            }
            DeveloperToolsRemoteConfigCard(
                remoteConfig = uiState.remoteConfig,
                isRefreshing = uiState.isRefreshingRemoteConfig,
                onRefreshClick = onRefreshRemoteConfigClick,
                modifier = Modifier.fillMaxWidth(),
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_back),
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeveloperToolsValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs)) {
        Text(text = label, color = ChalkakTheme.colors.textMuted, style = ChalkakTheme.typography.subheadline)
        Text(text = value, color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.callout)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DeveloperToolsScreenPreview() {
    ChalkakTheme {
        DeveloperToolsScreen(
            uiState = DeveloperToolsUiState(
                versionName = "0.2.0",
                versionCode = 24,
                buildType = "debug",
                applicationId = "com.stonefive.chalkak.debug",
                serverEnvironment = "dev.example.com",
                serverBaseUrl = "https://dev.example.com/",
                remoteConfig = AppConfigDiagnostics(
                    entries = listOf(
                        AppConfigEntry("ai_review_enabled", "true", AppConfigSource.Default),
                        AppConfigEntry("photo_edit_enabled", "false", AppConfigSource.Remote),
                    ),
                    lastFetchStatus = AppConfigFetchStatus.Success,
                    lastFetchedAtMillis = 1_793_000_000_000L,
                ),
            ),
            onBackClick = {},
            onRefreshRemoteConfigClick = {},
        )
    }
}
