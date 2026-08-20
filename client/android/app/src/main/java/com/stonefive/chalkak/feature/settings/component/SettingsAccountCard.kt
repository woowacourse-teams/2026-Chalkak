package com.stonefive.chalkak.feature.settings.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun SettingsAccountCard(
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            text = "로그아웃",
            onClick = onLogoutClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_log_out),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(12.dp),
                )
            },
        )

        SettingsDivider(modifier = Modifier.fillMaxWidth())

        SettingsRow(
            text = "회원탈퇴",
            textColor = Color(0xFFFF4D4F),
            onClick = onWithdrawClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SettingsAccountCardPreview() {
    ChalkakTheme {
        SettingsAccountCard(
            onLogoutClick = {},
            onWithdrawClick = {},
            modifier = Modifier
                .padding(25.dp)
                .fillMaxWidth(),
        )
    }
}
