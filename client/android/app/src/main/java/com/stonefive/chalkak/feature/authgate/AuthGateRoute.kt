package com.stonefive.chalkak.feature.authgate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun AuthGateRoute(
    loginRequiredContent: @Composable (Modifier) -> Unit,
    appAccessibleContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuthGateContent(
        uiState = uiState,
        loginRequiredContent = loginRequiredContent,
        appAccessibleContent = appAccessibleContent,
        modifier = modifier,
    )
}

@Composable
private fun AuthGateContent(
    uiState: AuthGateUiState,
    loginRequiredContent: @Composable (Modifier) -> Unit,
    appAccessibleContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        AuthGateUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(ChalkakTheme.colors.background),
            )
        }

        AuthGateUiState.LoginRequired -> key(AuthGateUiState.LoginRequired) {
            loginRequiredContent(modifier)
        }

        AuthGateUiState.AppAccessible -> key(AuthGateUiState.AppAccessible) {
            appAccessibleContent(modifier)
        }
    }
}
