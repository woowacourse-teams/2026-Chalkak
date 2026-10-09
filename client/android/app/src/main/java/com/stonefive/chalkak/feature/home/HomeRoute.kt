package com.stonefive.chalkak.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.Post
import java.time.LocalDate

@Composable
fun HomeRoute(
    onOpenPhotoUpload: () -> Unit,
    onNavigateToBottomBar: (ChalkakBottomBarItem) -> Unit,
    onOpenRecord: () -> Unit,
    onOpenDisplay: (LocalDate) -> Unit,
    onOpenFeed: (Post) -> Unit,
    onNotificationClick: () -> Unit,
    onIssueClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HomeScreen(
        uiState = uiState,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onOpenRecord = onOpenRecord,
        onOpenDisplay = onOpenDisplay,
        onOpenFeed = onOpenFeed,
        onNavigateToBottomBar = { item ->
            if (item == ChalkakBottomBarItem.TODAY) viewModel.refresh() else onNavigateToBottomBar(item)
        },
        onRetry = viewModel::refresh,
        onNotificationClick = onNotificationClick,
        onIssueClick = onIssueClick,
        modifier = modifier,
    )
}
