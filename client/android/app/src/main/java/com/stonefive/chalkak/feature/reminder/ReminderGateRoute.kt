package com.stonefive.chalkak.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.ReminderPreference

@Composable
fun ReminderGateRoute(
    reminderRequiredContent: @Composable (Modifier) -> Unit,
    configuredContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReminderGateViewModel = viewModel(factory = ReminderGateViewModel.Factory),
) {
    val preference by viewModel.preference.collectAsStateWithLifecycle()

    when (preference) {
        ReminderPreference.Loading -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(ChalkakTheme.colors.background),
        )

        ReminderPreference.Unconfigured -> reminderRequiredContent(modifier)

        ReminderPreference.Disabled,
        is ReminderPreference.Enabled,
        -> configuredContent(modifier)
    }
}
