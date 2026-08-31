package com.stonefive.chalkak

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.authgate.AuthGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel

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
                    AuthGateRoute()
                }
            }
        }
    }
}
