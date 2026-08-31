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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.authgate.AuthGateUiState
import com.stonefive.chalkak.feature.authgate.AuthGateViewModel
import com.stonefive.chalkak.feature.versiongate.VersionCheckFailureScreen
import com.stonefive.chalkak.feature.versiongate.VersionGateLoadingScreen
import com.stonefive.chalkak.feature.versiongate.VersionGateUiState
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
                ChalkakApp(
                    versionGateViewModel = versionGateViewModel,
                    onStartImmediateUpdate = {
                        appUpdateGateway.startImmediateUpdate(updateLauncher)
                    },
                    onImmediateUpdateStartFailed = ::finish,
                )
            }
        }
    }
}

@Composable
private fun ChalkakApp(
    versionGateViewModel: VersionGateViewModel,
    onStartImmediateUpdate: () -> Boolean,
    onImmediateUpdateStartFailed: () -> Unit,
) {
    val versionGateUiState by versionGateViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPassedVersionGate by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, versionGateViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                versionGateViewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(versionGateUiState) {
        if (versionGateUiState == VersionGateUiState.Accessible) {
            hasPassedVersionGate = true
        }

        if (versionGateUiState == VersionGateUiState.UpdateRequired) {
            if (onStartImmediateUpdate()) {
                versionGateViewModel.onImmediateUpdateStarted()
            } else {
                onImmediateUpdateStartFailed()
            }
        }
    }

    if (hasPassedVersionGate) {
        Box(modifier = Modifier.fillMaxSize()) {
            AuthGateContent()

            when (versionGateUiState) {
                VersionGateUiState.Accessible -> Unit

                VersionGateUiState.CheckFailed -> VersionCheckFailureScreen(
                    onRetryClick = versionGateViewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

                VersionGateUiState.Checking,
                VersionGateUiState.UpdateRequired,
                VersionGateUiState.UpdateInProgress,
                -> VersionGateLoadingScreen(modifier = Modifier.fillMaxSize())
            }
        }
    } else if (versionGateUiState == VersionGateUiState.CheckFailed) {
        VersionCheckFailureScreen(onRetryClick = versionGateViewModel::retry)
    } else {
        VersionGateLoadingScreen()
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
