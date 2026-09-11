package com.stonefive.chalkak.feature.versiongate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

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

@Composable
fun VersionGateLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .safeDrawingPadding(),
    )
}

@Composable
fun VersionCheckFailureScreen(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .safeDrawingPadding()
            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "업데이트를 확인할 수 없어요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "네트워크 연결을 확인한 후 다시 시도해 주세요.",
            modifier = Modifier.padding(top = ChalkakTheme.spacing.sm),
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.callout,
            textAlign = TextAlign.Center,
        )
        ChalkakButton(
            text = "다시 시도",
            onClick = onRetryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun VersionGateLoadingScreenPreview() {
    ChalkakTheme {
        VersionGateLoadingScreen()
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun VersionCheckFailureScreenPreview() {
    ChalkakTheme {
        VersionCheckFailureScreen(onRetryClick = {})
    }
}
