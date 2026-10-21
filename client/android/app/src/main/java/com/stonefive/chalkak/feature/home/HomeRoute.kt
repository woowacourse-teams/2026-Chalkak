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
    onIssueClick: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        var isResumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!isResumed) {
                    isResumed = true
                    viewModel.onResume()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    isResumed = false
                    viewModel.onPause()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        ) {
            if (!isResumed) {
                isResumed = true
                viewModel.onResume()
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
        }
    }
    HomeScreen(
        uiState = uiState,
        onOpenPhotoUpload = onOpenPhotoUpload,
        onOpenRecord = onOpenRecord,
        onOpenDisplay = onOpenDisplay,
        onOpenFeed = onOpenFeed,
        onNavigateToBottomBar = onNavigateToBottomBar,
        onRetry = viewModel::refresh,
        onNotificationClick = onNotificationClick,
        onIssueClick = onIssueClick,
        onStartFeedback = viewModel::startFeedback,
        modifier = modifier,
        onLoadMoreBanners = viewModel::loadMoreBanners,
        onDismissFeedbackMessage = viewModel::dismissFeedbackMessage,
    )
}
