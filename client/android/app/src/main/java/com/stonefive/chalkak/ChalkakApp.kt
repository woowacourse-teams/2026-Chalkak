package com.stonefive.chalkak

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.feature.authgate.AuthGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateRoute
import com.stonefive.chalkak.feature.versiongate.VersionGateViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Login
import com.stonefive.chalkak.navigation.Today

@Composable
fun ChalkakApp(
    versionGateViewModel: VersionGateViewModel,
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
                    modifier = contentModifier,
                    startDestination = Login,
                )
            },
            appAccessibleContent = { contentModifier ->
                ChalkakNavHost(
                    modifier = contentModifier,
                    startDestination = Today,
                )
            },
        )
    }
}
