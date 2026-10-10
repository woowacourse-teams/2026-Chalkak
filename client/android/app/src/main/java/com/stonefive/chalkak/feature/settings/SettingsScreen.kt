package com.stonefive.chalkak.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialog
import com.stonefive.chalkak.core.designsystem.component.dialog.ChalkakConfirmDialogStyle
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.settings.component.SettingsAccountCard
import com.stonefive.chalkak.feature.settings.component.SettingsAppCard
import com.stonefive.chalkak.feature.settings.component.SettingsFeedbackCard
import com.stonefive.chalkak.feature.settings.component.SettingsInformationCard
import com.stonefive.chalkak.feature.settings.component.SettingsLoginButton
import com.stonefive.chalkak.feature.settings.component.SettingsPushNotificationCard
import com.stonefive.chalkak.feature.settings.component.SettingsSectionLabel

@Composable
fun SettingsRoute(
    onNavigateToSignature: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenReminder: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onOpenPhotoUpload: () -> Unit,
    signatureUpdateUrl: String? = null,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var deviceNotificationsEnabled by remember {
        mutableStateOf(context.areDeviceNotificationsEnabled())
    }
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                deviceNotificationsEnabled = context.areDeviceNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(signatureUpdateUrl) {
        signatureUpdateUrl?.let(viewModel::applySignatureUpdate)
    }

    SettingsScreen(
        uiState = uiState,
        onLoginClick = viewModel::startLogin,
        onChangeSignatureClick = onNavigateToSignature,
        onPrivacyPolicyClick = onOpenPrivacyPolicy,
        onTermsClick = onOpenTerms,
        onFeedbackClick = onOpenFeedback,
        onReminderClick = onOpenReminder,
        onLogoutClick = viewModel::showLogoutDialog,
        onWithdrawClick = viewModel::showWithdrawDialog,
        onAccountDialogConfirm = viewModel::confirmAccountAction,
        onAccountDialogDismiss = viewModel::dismissAccountDialog,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onAddClick = onOpenPhotoUpload,
        onTopicPushChanged = viewModel::updateTopicPushEnabled,
        onModerationPushChanged = viewModel::updateModerationPushEnabled,
        deviceNotificationsEnabled = deviceNotificationsEnabled,
        onOpenDeviceNotificationSettings = {
            context.openDeviceNotificationSettings()
        },
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
    onReminderClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onAccountDialogConfirm: () -> Unit,
    onAccountDialogDismiss: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    onTopicPushChanged: (Boolean) -> Unit = {},
    onModerationPushChanged: (Boolean) -> Unit = {},
    deviceNotificationsEnabled: Boolean = true,
    onOpenDeviceNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
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
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            SettingsSectionLabel(text = "앱 설정")

            Spacer(modifier = Modifier.height(16.dp))

            SettingsAppCard(
                showSignature = uiState.isLoggedIn,
                signatureUrl = uiState.signatureUrl,
                onReminderClick = onReminderClick,
                onChangeSignatureClick = onChangeSignatureClick,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isLoggedIn) {
                Spacer(modifier = Modifier.height(36.dp))

                SettingsSectionLabel(text = "푸시 알림")

                Spacer(modifier = Modifier.height(16.dp))

                SettingsPushNotificationCard(
                    settings = uiState.pushSettings,
                    isLoading = uiState.isPushSettingsLoading,
                    isSaving = uiState.isPushSettingsSaving,
                    deviceNotificationsEnabled = deviceNotificationsEnabled,
                    onTopicPushChanged = onTopicPushChanged,
                    onModerationPushChanged = onModerationPushChanged,
                    onOpenDeviceNotificationSettings = onOpenDeviceNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

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

            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
        }
    }
}

private fun Context.areDeviceNotificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

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
private fun AuthenticatedSettingsScreenPreview() {
    ChalkakTheme {
        SettingsScreenPreview(
            uiState = SettingsUiState(
                isLoggedIn = true,
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
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
        onReminderClick = {},
        onLogoutClick = {},
        onWithdrawClick = {},
        onAccountDialogConfirm = {},
        onAccountDialogDismiss = {},
        onNavigateToBottomBar = {},
        onAddClick = {},
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
