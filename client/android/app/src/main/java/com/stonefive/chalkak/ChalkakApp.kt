package com.stonefive.chalkak

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.feature.authgate.AuthGateRoute
import com.stonefive.chalkak.feature.reminder.ReminderGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Login
import com.stonefive.chalkak.navigation.ReminderTime
import com.stonefive.chalkak.navigation.Today

@Composable
fun ChalkakApp(
    versionGateViewModel: VersionGateViewModel,
    analyticsTracker: AnalyticsTracker,
    onStartImmediateUpdate: () -> Boolean,
    onImmediateUpdateStartFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VersionGateRoute(
        viewModel = versionGateViewModel,
        onStartImmediateUpdate = onStartImmediateUpdate,
        onImmediateUpdateStartFailed = onImmediateUpdateStartFailed,
        modifier = modifier,
    ) {
        AuthGateRoute(
            loginRequiredContent = { contentModifier ->
                ChalkakNavHost(
                    analyticsTracker = analyticsTracker,
                    modifier = contentModifier,
                    startDestination = Login,
                )
            },
            appAccessibleContent = { contentModifier ->
                ReminderGateRoute(
                    reminderRequiredContent = { gateModifier ->
                        ChalkakNavHost(
                            analyticsTracker = analyticsTracker,
                            modifier = gateModifier,
                            startDestination = ReminderTime(),
                        )
                    },
                    configuredContent = { gateModifier ->
                        ChalkakNavHost(
                            analyticsTracker = analyticsTracker,
                            modifier = gateModifier,
                            startDestination = Today,
                        )
                    },
                    modifier = contentModifier,
                )
            },
        )
    }
}
