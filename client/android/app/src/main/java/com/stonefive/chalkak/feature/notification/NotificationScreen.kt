package com.stonefive.chalkak.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.feature.notification.component.NotificationListItem
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun NotificationRoute(
    onBackClick: () -> Unit,
    onNotificationClick: (NotificationItemUiState) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    UiMessageEffect(uiState.value.pendingMessage, viewModel::onMessageShown)

    NotificationScreen(
        uiState = uiState.value,
        onBackClick = onBackClick,
        onRefresh = viewModel::refresh,
        onLoadNextPage = viewModel::loadNextPage,
        onNotificationClick = { item ->
            coroutineScope.launch {
                if (viewModel.markAsRead(item.id)) onNotificationClick(item)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun NotificationScreen(
    uiState: NotificationUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onLoadNextPage: () -> Unit = {},
    onNotificationClick: (NotificationItemUiState) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var lastRequestedPage by remember { mutableIntStateOf(-1) }
    val retryRefresh = {
        lastRequestedPage = -1
        onRefresh()
    }

    LaunchedEffect(
        listState,
        uiState.notifications.size,
        uiState.hasNext,
        uiState.currentPage,
        uiState.isLoading,
        uiState.isLoadingMore,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.map { lastVisibleIndex ->
            lastVisibleIndex != null && lastVisibleIndex >= uiState.notifications.size - 4
        }.distinctUntilChanged()
            .collect { nearEnd ->
                val nextPage = uiState.currentPage + 1
                if (nearEnd && uiState.hasNext && !uiState.isLoading && !uiState.isLoadingMore &&
                    lastRequestedPage != nextPage
                ) {
                    lastRequestedPage = nextPage
                    onLoadNextPage()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        NotificationTopBar(
            onBackClick = onBackClick,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            uiState.isLoading && uiState.notifications.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ChalkakTheme.colors.iconPrimary)
            }

            uiState.notifications.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ChalkakTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage ?: "아직 알림이 없어요.",
                    color = ChalkakTheme.colors.textInactive,
                    style = ChalkakTheme.typography.body,
                )
                if (uiState.errorMessage != null) {
                    Text(
                        text = "다시 시도",
                        modifier = Modifier
                            .clickable(onClick = retryRefresh)
                            .padding(top = ChalkakTheme.spacing.md),
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
            ) {
                items(
                    items = uiState.notifications,
                    key = { notification -> notification.id },
                ) { notification ->
                    NotificationListItem(
                        item = notification,
                        onClick = { onNotificationClick(notification) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = ChalkakTheme.spacing.screenHorizontal),
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
                        thickness = DividerDefaults.Thickness,
                        color = ChalkakTheme.colors.border,
                    )
                }

                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ChalkakTheme.spacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = ChalkakTheme.colors.iconPrimary)
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = retryRefresh)
                                .padding(ChalkakTheme.spacing.lg),
                            color = ChalkakTheme.colors.textInactive,
                            style = ChalkakTheme.typography.caption,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationScreenPreview() {
    ChalkakTheme {
        NotificationScreen(
            uiState = NotificationUiState(notifications = previewNotifications, hasUnread = true),
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun EmptyNotificationScreenPreview() {
    ChalkakTheme {
        NotificationScreen(
            uiState = NotificationUiState(),
            onBackClick = {},
        )
    }
}
