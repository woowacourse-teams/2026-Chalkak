package com.stonefive.chalkak.feature.versiongate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VersionGateRoute(
    viewModel: VersionGateViewModel,
    onStartImmediateUpdate: () -> Boolean,
    onImmediateUpdateStartFailed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status == VersionGateStatus.UpdateRequired) {
            if (onStartImmediateUpdate()) {
                viewModel.onImmediateUpdateStarted()
            } else {
                onImmediateUpdateStartFailed()
            }
        }
    }

    if (uiState.hasPassedVersionGate) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            when (uiState.status) {
                VersionGateStatus.Accessible -> Unit

                VersionGateStatus.CheckFailed -> VersionCheckFailureScreen(
                    onRetryClick = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )

                VersionGateStatus.Checking,
                VersionGateStatus.UpdateRequired,
                VersionGateStatus.UpdateInProgress,
                -> VersionGateLoadingScreen(modifier = Modifier.fillMaxSize())
            }
        }
    } else if (uiState.status == VersionGateStatus.CheckFailed) {
        VersionCheckFailureScreen(
            onRetryClick = viewModel::retry,
            modifier = modifier,
        )
    } else {
        VersionGateLoadingScreen(modifier = modifier)
    }
}
