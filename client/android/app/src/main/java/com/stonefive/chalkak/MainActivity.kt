package com.stonefive.chalkak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.stonefive.chalkak.feature.authgate.AuthGateUiState
import com.stonefive.chalkak.feature.authgate.AuthGateViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Login
import com.stonefive.chalkak.navigation.Today

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            ChalkakTheme {
                ChalkakApp()
            }
        }
    }
}

@Composable
private fun ChalkakApp(viewModel: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        AuthGateUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChalkakTheme.colors.background),
            )
        }

        AuthGateUiState.LoginRequired -> key(AuthGateUiState.LoginRequired) {
            ChalkakNavHost(startDestination = Login)
        }

        AuthGateUiState.AppAccessible -> key(AuthGateUiState.AppAccessible) {
            ChalkakNavHost(startDestination = Today)
        }
    }
}
