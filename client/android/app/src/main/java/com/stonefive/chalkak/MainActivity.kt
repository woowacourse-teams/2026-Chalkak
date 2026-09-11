package com.stonefive.chalkak

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel

class MainActivity : ComponentActivity() {
    private val versionGateViewModel: VersionGateViewModel by viewModels {
        VersionGateViewModel.Factory
    }

    private val appContainer: AppContainer
        get() = (application as ChalkakApplication).appContainer

    private val appUpdateGateway: AppUpdateGateway
        get() = appContainer.appUpdateGateway

    private val analyticsTracker: AnalyticsTracker
        get() = appContainer.analyticsTracker

    // MainActivity는 ComponentActivity를 상속하므로, 해당 Activity 결과 API는 FragmentActivity를 사용하지 않는다.
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
                    analyticsTracker = analyticsTracker,
                    onStartImmediateUpdate = {
                        appUpdateGateway.startImmediateUpdate(updateLauncher)
                    },
                    onImmediateUpdateStartFailed = ::finish,
                )
            }
        }
    }
}
