package com.stonefive.chalkak.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.R
import com.stonefive.chalkak.buildvariant.BuildVariantSettingsEntry
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialog
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialogStyle
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar
import com.stonefive.chalkak.feature.settings.component.SettingsAccountCard
import com.stonefive.chalkak.feature.settings.component.SettingsAppCard
import com.stonefive.chalkak.feature.settings.component.SettingsFeedbackCard
import com.stonefive.chalkak.feature.settings.component.SettingsInformationCard
import com.stonefive.chalkak.feature.settings.component.SettingsLoginButton
import com.stonefive.chalkak.feature.settings.component.SettingsNotificationEntryCard
import com.stonefive.chalkak.feature.settings.component.SettingsSectionLabel

@Composable
fun SettingsRoute(
    onNavigateToSignature: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBackClick: () -> Unit,
    signatureUpdateUrl: String? = null,
    onSignatureUpdateConsumed: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    LaunchedEffect(viewModel) {
        viewModel.refreshSignature()
    }

    LaunchedEffect(signatureUpdateUrl) {
        signatureUpdateUrl?.let {
            viewModel.applySignatureUpdate(it)
            onSignatureUpdateConsumed()
        }
    }

    SettingsScreen(
        uiState = uiState,
        onLoginClick = viewModel::startLogin,
        onChangeSignatureClick = onNavigateToSignature,
        onPrivacyPolicyClick = onOpenPrivacyPolicy,
        onTermsClick = onOpenTerms,
        onFeedbackClick = onOpenFeedback,
        onLogoutClick = viewModel::showLogoutDialog,
        onWithdrawClick = viewModel::showWithdrawDialog,
        onAccountDialogConfirm = viewModel::confirmAccountAction,
        onAccountDialogDismiss = viewModel::dismissAccountDialog,
        onBackClick = onBackClick,
        onNotificationSettingsClick = onOpenNotificationSettings,
        buildVariantContent = { BuildVariantSettingsEntry(modifier = Modifier.fillMaxWidth()) },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLoginClick: () -> Unit,
    onChangeSignatureClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onAccountDialogConfirm: () -> Unit,
    onAccountDialogDismiss: () -> Unit,
    onBackClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    buildVariantContent: @Composable () -> Unit = {},
) {
    uiState.accountDialog?.let { accountDialog ->
        ChalkakConfirmDialog(
            title = accountDialog.title,
            message = accountDialog.message,
            confirmText = accountDialog.confirmText,
            onConfirm = onAccountDialogConfirm,
            onDismiss = onAccountDialogDismiss,
            modifier = Modifier.width(AccountDialogWidth),
            confirmStyle = accountDialog.confirmStyle,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ChalkakTheme.colors.background,
        topBar = {
            NotificationTopBar(
                onBackClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                title = "설정",
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))

            if (uiState.isLoggedIn) {
                SettingsSectionLabel(text = "앱 설정")

                Spacer(modifier = Modifier.height(16.dp))

                SettingsAppCard(
                    showSignature = true,
                    signatureUrl = uiState.signatureUrl,
                    onChangeSignatureClick = onChangeSignatureClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsNotificationEntryCard(
                    onClick = onNotificationSettingsClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(36.dp))
            }

            SettingsSectionLabel(text = "계정")

            Spacer(modifier = Modifier.height(16.dp))

            when {
                uiState.isLoggedIn -> SettingsAccountCard(
                    onLogoutClick = onLogoutClick,
                    onWithdrawClick = onWithdrawClick,
                    enabled = !uiState.isAccountActionInProgress,
                    modifier = Modifier.fillMaxWidth(),
                )

                !uiState.isLoading -> SettingsLoginButton(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Spacer(modifier = Modifier.height(56.dp))
            }

            Spacer(modifier = Modifier.height(40.dp))

            SettingsSectionLabel(text = "피드백")

            Spacer(modifier = Modifier.height(16.dp))

            SettingsFeedbackCard(
                onFeedbackClick = onFeedbackClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(40.dp))

            SettingsSectionLabel(text = "정보 및 약관")

            Spacer(modifier = Modifier.height(16.dp))

            SettingsInformationCard(
                versionName = uiState.versionName,
                onPrivacyPolicyClick = onPrivacyPolicyClick,
                onTermsClick = onTermsClick,
                modifier = Modifier.fillMaxWidth(),
            )

            buildVariantContent()

            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
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
                signatureUrl = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.preview_signature}",
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
        onFeedbackClick = {},
        onLogoutClick = {},
        onWithdrawClick = {},
        onAccountDialogConfirm = {},
        onAccountDialogDismiss = {},
        onBackClick = {},
        onNotificationSettingsClick = {},
    )
}

private val SettingsAccountDialog.title: String
    get() = when (this) {
        SettingsAccountDialog.LOGOUT -> "로그아웃"
        SettingsAccountDialog.WITHDRAW -> "회원탈퇴"
    }

private val SettingsAccountDialog.message: String
    get() = when (this) {
        SettingsAccountDialog.LOGOUT -> "정말 로그아웃 하시겠습니까?"
        SettingsAccountDialog.WITHDRAW -> "정말 회원탈퇴 하시겠습니까?"
    }

private val SettingsAccountDialog.confirmText: String
    get() = title

private val SettingsAccountDialog.confirmStyle: ChalkakConfirmDialogStyle
    get() = when (this) {
        SettingsAccountDialog.LOGOUT -> ChalkakConfirmDialogStyle.PRIMARY
        SettingsAccountDialog.WITHDRAW -> ChalkakConfirmDialogStyle.DESTRUCTIVE
    }

private val AccountDialogWidth = 317.dp
