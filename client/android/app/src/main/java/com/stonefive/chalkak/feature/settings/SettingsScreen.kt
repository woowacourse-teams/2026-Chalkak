package com.stonefive.chalkak.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.settings.component.SettingsAccountCard
import com.stonefive.chalkak.feature.settings.component.SettingsInformationCard
import com.stonefive.chalkak.feature.settings.component.SettingsLoginButton
import com.stonefive.chalkak.feature.settings.component.SettingsSectionLabel
import com.stonefive.chalkak.feature.settings.component.SettingsSignatureCard

@Composable
fun SettingsRoute(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignature: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onOpenPhotoUpload: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                SettingsUiEvent.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        onLoginClick = onNavigateToLogin,
        onChangeSignatureClick = onNavigateToSignature,
        onPrivacyPolicyClick = onOpenPrivacyPolicy,
        onTermsClick = onOpenTerms,
        onLogoutClick = viewModel::logout,
        onWithdrawClick = viewModel::withdraw,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onAddClick = onOpenPhotoUpload,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLoginClick: () -> Unit,
    onChangeSignatureClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakTheme.colors.background,
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = ChalkakBottomBarItem.SETTINGS,
                onItemSelected = onNavigateToBottomBar,
                onAddClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            SettingsSectionLabel(text = "앱 설정")

            Spacer(modifier = Modifier.height(16.dp))

            when {
                uiState.isLoading -> Spacer(modifier = Modifier.height(56.dp))

                uiState.isLoggedIn -> SettingsSignatureCard(
                    signatureModel = uiState.signatureModel,
                    onChangeClick = onChangeSignatureClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> SettingsLoginButton(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            SettingsSectionLabel(text = "정보 및 약관")

            Spacer(modifier = Modifier.height(16.dp))

            SettingsInformationCard(
                versionName = uiState.versionName,
                onPrivacyPolicyClick = onPrivacyPolicyClick,
                onTermsClick = onTermsClick,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isLoggedIn) {
                Spacer(modifier = Modifier.height(40.dp))
                SettingsAccountCard(
                    onLogoutClick = onLogoutClick,
                    onWithdrawClick = onWithdrawClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AuthenticatedSettingsScreenPreview() {
    ChalkakTheme {
        SettingsScreenPreview(
            uiState = SettingsUiState(
                isLoggedIn = true,
                signatureModel = R.drawable.preview_signature,
                versionName = "1.0",
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GuestSettingsScreenPreview() {
    ChalkakTheme {
        SettingsScreenPreview(
            uiState = SettingsUiState(
                versionName = "1.0",
            ),
        )
    }
}

@Composable
private fun SettingsScreenPreview(uiState: SettingsUiState) {
    SettingsScreen(
        uiState = uiState,
        onLoginClick = {},
        onChangeSignatureClick = {},
        onPrivacyPolicyClick = {},
        onTermsClick = {},
        onLogoutClick = {},
        onWithdrawClick = {},
        onNavigateToBottomBar = {},
        onAddClick = {},
    )
}
