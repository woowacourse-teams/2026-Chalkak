package com.stonefive.chalkak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.core.notification.restoreFrom
import com.stonefive.chalkak.core.notification.saveTo
import com.stonefive.chalkak.feature.home.HomeViewModel
import com.stonefive.chalkak.feature.versiongate.VersionGateStatus
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel

class MainActivity : ComponentActivity() {
    private val versionGateViewModel: VersionGateViewModel by viewModels {
        VersionGateViewModel.Factory
    }
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory
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
    ) {
        versionGateViewModel.onImmediateUpdateFinished()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            enqueueNotificationNavigation(intent)
        } else {
            appContainer.pendingNotificationNavigation.restoreFrom(savedInstanceState)
        }
        splashScreen.setKeepOnScreenCondition {
            when (versionGateViewModel.uiState.value.status) {
                VersionGateStatus.Checking -> true

                VersionGateStatus.Accessible -> !homeViewModel.isReadyForInitialDisplay.value

                VersionGateStatus.CheckFailed,
                VersionGateStatus.UpdateRequired,
                VersionGateStatus.UpdateInProgress,
                -> false
            }
        }
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
                    homeViewModel = homeViewModel,
                    analyticsTracker = analyticsTracker,
                    onStartImmediateUpdate = {
                        appUpdateGateway.startImmediateUpdate(updateLauncher)
                    },
                    onImmediateUpdateStartFailed = {
                        appUpdateGateway.storeUrl?.let { url ->
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueNotificationNavigation(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        appContainer.pendingNotificationNavigation.saveTo(outState)
    }

    override fun onResume() {
        super.onResume()
        appContainer.syncPushDevice()
    }

    private fun enqueueNotificationNavigation(intent: Intent?) {
        NotificationPushData.from(intent)?.let(appContainer.pendingNotificationNavigation::enqueue)
    }
}
