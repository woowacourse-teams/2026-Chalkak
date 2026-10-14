package com.stonefive.chalkak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.feature.authgate.AuthGateRoute
import com.stonefive.chalkak.feature.home.HomeViewModel
import com.stonefive.chalkak.feature.versiongate.VersionGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Home
import com.stonefive.chalkak.navigation.Login

@Composable
fun ChalkakApp(
    versionGateViewModel: VersionGateViewModel,
    homeViewModel: HomeViewModel,
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
        LaunchedEffect(homeViewModel) {
            homeViewModel.startPreloading()
        }
        AuthGateRoute(
            loginRequiredContent = { contentModifier ->
                ChalkakNavHost(
                    analyticsTracker = analyticsTracker,
                    homeViewModel = homeViewModel,
                    modifier = contentModifier,
                    startDestination = Login,
                )
            },
            appAccessibleContent = { contentModifier ->
                ChalkakNavHost(
                    analyticsTracker = analyticsTracker,
                    homeViewModel = homeViewModel,
                    modifier = contentModifier,
                    startDestination = Home,
                )
            },
        )
    }
}
