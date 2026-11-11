package com.stonefive.chalkak.feature.developertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.component.toggle.ChalkakSwitch
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/**
 * 개발자 도구의 인증 카드. 토큰 만료 시각을 보여 주고, 만료 상황(AT 강제 만료, RT 폐기, RT 폐기 후 30초 뒤 AT 만료, 동시 요청, 갱신 네트워크 실패)을 만든다.
 * 로그인하지 않았으면 버튼과 스위치를 끈다.
 */
@Composable
fun DeveloperToolsAuthCard(
    auth: DeveloperAuthUiState,
    onExpireAccessTokenClick: () -> Unit,
    onRevokeRefreshTokenClick: () -> Unit,
    onRevokeRefreshTokenWithDelayedExpiryClick: () -> Unit,
    onSendConcurrentRequestsClick: () -> Unit,
    onRefreshNetworkFailureChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionsEnabled = auth.isSignedIn && !auth.isRunning
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
                text = stringResource(R.string.developer_tools_auth),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
            )
            val notSignedIn = stringResource(R.string.developer_tools_auth_not_signed_in)
            AuthValue(
                label = stringResource(R.string.developer_tools_auth_access_expires_at),
                value = auth.accessTokenExpiresAt ?: notSignedIn,
            )
            AuthValue(
                label = stringResource(R.string.developer_tools_auth_access_status),
                value = auth.accessTokenStatus ?: notSignedIn,
            )
            AuthValue(
                label = stringResource(R.string.developer_tools_auth_refresh_expires_at),
                value = auth.refreshTokenExpiresAt ?: notSignedIn,
            )
            AuthValue(
                label = stringResource(R.string.developer_tools_auth_last_refresh),
                value = auth.lastRefresh ?: stringResource(R.string.developer_tools_auth_no_result),
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_auth_expire_access_token),
                onClick = onExpireAccessTokenClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_auth_revoke_refresh_token),
                onClick = onRevokeRefreshTokenClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_auth_revoke_refresh_token_delayed),
                onClick = onRevokeRefreshTokenWithDelayedExpiryClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            )
            ChalkakOutlinedButton(
                text = stringResource(R.string.developer_tools_auth_concurrent_requests),
                onClick = onSendConcurrentRequestsClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.developer_tools_auth_refresh_network_failure),
                    modifier = Modifier.weight(1f),
                    color = ChalkakTheme.colors.textPrimary,
                    style = ChalkakTheme.typography.callout,
                )
                ChalkakSwitch(
                    checked = auth.isRefreshNetworkFailureEnabled,
                    onCheckedChange = onRefreshNetworkFailureChange,
                    enabled = auth.isSignedIn,
                )
            }
            AuthValue(
                label = stringResource(R.string.developer_tools_auth_last_result),
                value = auth.lastResult ?: stringResource(R.string.developer_tools_auth_no_result),
            )
        }
    }
}

@Composable
private fun AuthValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.xs)) {
        Text(text = label, color = ChalkakTheme.colors.textMuted, style = ChalkakTheme.typography.subheadline)
        Text(text = value, color = ChalkakTheme.colors.textPrimary, style = ChalkakTheme.typography.callout)
    }
}

@Preview(name = "로그인됨", showBackground = true) // 정상 상태
@Composable
private fun DeveloperToolsAuthCardPreview() {
    ChalkakTheme {
        DeveloperToolsAuthCard(
            auth = DeveloperAuthUiState(
                isSignedIn = true,
                accessTokenExpiresAt = "2026-11-11 15:30:00",
                refreshTokenExpiresAt = "2026-12-11 15:15:00",
                accessTokenStatus = "정상",
                lastRefresh = "18:04:10 갱신됨 (누적 1회)",
                lastResult = "갱신 1회 · 성공 5/5",
            ),
            onExpireAccessTokenClick = {},
            onRevokeRefreshTokenClick = {},
            onRevokeRefreshTokenWithDelayedExpiryClick = {},
            onSendConcurrentRequestsClick = {},
            onRefreshNetworkFailureChange = {},
        )
    }
}

@Preview(name = "강제 만료", showBackground = true)
@Composable
private fun DeveloperToolsAuthCardExpiredPreview() {
    ChalkakTheme {
        DeveloperToolsAuthCard(
            auth = DeveloperAuthUiState(
                isSignedIn = true,
                accessTokenExpiresAt = "2026-11-11 15:30:00",
                refreshTokenExpiresAt = "2026-12-11 15:15:00",
                accessTokenStatus = "무효(강제 만료됨)",
                lastResult = "Access 토큰을 무효 값으로 바꿨어요",
            ),
            onExpireAccessTokenClick = {},
            onRevokeRefreshTokenClick = {},
            onRevokeRefreshTokenWithDelayedExpiryClick = {},
            onSendConcurrentRequestsClick = {},
            onRefreshNetworkFailureChange = {},
        )
    }
}

@Preview(name = "로그인 안 됨", showBackground = true)
@Composable
private fun DeveloperToolsAuthCardSignedOutPreview() {
    ChalkakTheme {
        DeveloperToolsAuthCard(
            auth = DeveloperAuthUiState(),
            onExpireAccessTokenClick = {},
            onRevokeRefreshTokenClick = {},
            onRevokeRefreshTokenWithDelayedExpiryClick = {},
            onSendConcurrentRequestsClick = {},
            onRefreshNetworkFailureChange = {},
        )
    }
}
