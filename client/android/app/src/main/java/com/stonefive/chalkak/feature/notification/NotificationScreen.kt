package com.stonefive.chalkak.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.notification.component.NotificationListItem
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar

@Composable
fun NotificationRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = viewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    NotificationScreen(
        uiState = uiState.value,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
fun NotificationScreen(
    uiState: NotificationUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        ) {
            items(
                items = uiState.notifications,
                key = { notification -> notification.id },
            ) { notification ->
                NotificationListItem(
                    item = notification,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = DividerDefaults.Thickness,
                    color = ChalkakTheme.colors.border,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationScreenPreview() {
    ChalkakTheme {
        NotificationScreen(
            uiState = NotificationUiState(
                notifications = previewNotifications,
            ),
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
