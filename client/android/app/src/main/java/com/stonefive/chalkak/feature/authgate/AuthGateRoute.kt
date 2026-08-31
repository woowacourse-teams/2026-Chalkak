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
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Login
import com.stonefive.chalkak.navigation.Today

@Composable
fun AuthGateRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuthGateContent(
        uiState = uiState,
        modifier = modifier,
    )
}

@Composable
private fun AuthGateContent(
    uiState: AuthGateUiState,
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
            ChalkakNavHost(
                modifier = modifier,
                startDestination = Login,
            )
        }

        AuthGateUiState.AppAccessible -> key(AuthGateUiState.AppAccessible) {
            ChalkakNavHost(
                modifier = modifier,
                startDestination = Today,
            )
        }
    }
}
