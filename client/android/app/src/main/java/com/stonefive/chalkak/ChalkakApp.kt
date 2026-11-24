package com.stonefive.chalkak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.buildvariant.BuildVariantAppOverlay
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
        Box(modifier = Modifier.fillMaxSize()) {
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
            // debug 빌드에서만 토큰 갱신 순간 위쪽에 작은 표시를 띄운다. release는 빈 함수다.
            BuildVariantAppOverlay(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
