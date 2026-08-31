package com.stonefive.chalkak

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.authgate.AuthGateUiState
import com.stonefive.chalkak.feature.authgate.AuthGateViewModel
import com.stonefive.chalkak.feature.versiongate.VersionGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Login
import com.stonefive.chalkak.navigation.Today

class MainActivity : ComponentActivity() {
    private val versionGateViewModel: VersionGateViewModel by viewModels {
        VersionGateViewModel.Factory
    }

    private val appUpdateGateway: AppUpdateGateway
        get() = (application as ChalkakApplication).appContainer.appUpdateGateway

    // MainActivity extends ComponentActivity, so this Activity Result API call does not use FragmentActivity.
    @Suppress("InvalidFragmentVersionForActivityResult")
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            versionGateViewModel.onImmediateUpdateFinished()
        } else {
            finish()
        }
    }

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
                VersionGateRoute(
                    viewModel = versionGateViewModel,
                    onStartImmediateUpdate = {
                        appUpdateGateway.startImmediateUpdate(updateLauncher)
                    },
                    onImmediateUpdateStartFailed = ::finish,
                ) {
                    AuthGateContent()
                }
            }
        }
    }
}

@Composable
private fun AuthGateContent(viewModel: AuthGateViewModel = viewModel(factory = AuthGateViewModel.Factory)) {
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
