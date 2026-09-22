package com.stonefive.chalkak.feature.reminder

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.reminder.component.ReminderTimeOptionRow

@Composable
fun ReminderTimeRoute(
    onConfigured: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReminderTimeViewModel = viewModel(factory = ReminderTimeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.saveSelection()
        } else {
            Toast
                .makeText(
                    context,
                    "알림을 받으려면 알림 권한을 허용해 주세요.",
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    LaunchedEffect(uiState.saveStatus) {
        if (uiState.saveStatus == ReminderSaveStatus.SAVED) onConfigured()
    }

    ReminderTimeScreen(
        uiState = uiState,
        onOptionClick = viewModel::selectOption,
        onCustomTimeClick = {
            TimePickerDialog(
                context,
                R.style.Theme_Chalkak_TimePickerSpinner,
                { _, hour, minute -> viewModel.selectCustomTime(hour, minute) },
                uiState.customHour ?: DEFAULT_CUSTOM_HOUR,
                uiState.customMinute ?: DEFAULT_CUSTOM_MINUTE,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        onConfirmClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.saveSelection()
            }
        },
        onSkipClick = viewModel::disable,
        modifier = modifier,
        enabled = uiState.saveStatus != ReminderSaveStatus.SAVING,
    )
}

@Composable
fun ReminderTimeScreen(
    uiState: ReminderTimeUiState,
    onOptionClick: (ReminderTimeOption) -> Unit,
    onCustomTimeClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .systemBarsPadding()
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                top = 50.dp,
                end = ChalkakTheme.spacing.screenHorizontal,
                bottom = ChalkakTheme.spacing.lg,
            ),
    ) {
        Text(
            text = "언제 알려드릴까요?",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )

        Text(
            text = "주제는 밤 12시에 변경돼요.\n날마다 주제를 알림으로 알려드릴게요.",
            modifier = Modifier.padding(top = ChalkakTheme.spacing.lg),
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.body,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChalkakTheme.spacing.xxl + ChalkakTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReminderTimeOption.entries.forEach { option ->
                val isCustom = option == ReminderTimeOption.CUSTOM
                ReminderTimeOptionRow(
                    label = option.label,
                    description = if (isCustom) uiState.customTimeLabel else option.description,
                    selected = uiState.selectedOption == option,
                    onClick = {
                        if (isCustom) {
                            onCustomTimeClick()
                        } else {
                            onOptionClick(option)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "알림 따로 필요 없어요!",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(
                    role = Role.Button,
                    onClick = onSkipClick,
                ).padding(ChalkakTheme.spacing.sm),
            color = ChalkakTheme.colors.textInactive,
            style = ChalkakTheme.typography.subheadline.copy(
                textDecoration = TextDecoration.Underline,
            ),
        )

        ChalkakButton(
            text = "이 시간으로 정할게요",
            onClick = onConfirmClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChalkakTheme.spacing.md,
                    top = ChalkakTheme.spacing.md,
                    end = ChalkakTheme.spacing.md,
                ),
            enabled = enabled,
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ReminderTimeScreenPreview() {
    ChalkakTheme {
        ReminderTimeScreen(
            uiState = ReminderTimeUiState(),
            onOptionClick = {},
            onCustomTimeClick = {},
            onConfirmClick = {},
            onSkipClick = {},
        )
    }
}

private const val DEFAULT_CUSTOM_HOUR = 18
private const val DEFAULT_CUSTOM_MINUTE = 0
