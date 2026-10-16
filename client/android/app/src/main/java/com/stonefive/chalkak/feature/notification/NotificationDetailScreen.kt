package com.stonefive.chalkak.feature.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationDetailRoute(
    notificationId: String,
    onBackClick: () -> Unit,
    onOpenNotificationList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationDetailViewModel = viewModel(
        key = notificationId,
        factory = NotificationDetailViewModel.factory(notificationId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onOpenNotificationList = onOpenNotificationList,
        modifier = modifier,
    )
}

@Composable
fun NotificationDetailScreen(
    uiState: NotificationDetailUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenNotificationList: () -> Unit,
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
            title = "알림 상세",
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ChalkakTheme.colors.iconPrimary)
            }

            uiState.detail == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ChalkakTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage ?: "알림을 불러오지 못했어요.",
                    color = ChalkakTheme.colors.textInactive,
                    style = ChalkakTheme.typography.body,
                )
                Row(modifier = Modifier.padding(top = ChalkakTheme.spacing.md)) {
                    Text(
                        text = "다시 시도",
                        modifier = Modifier
                            .clickable(onClick = onRetry)
                            .padding(ChalkakTheme.spacing.sm),
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                    Text(
                        text = "알림함으로",
                        modifier = Modifier
                            .clickable(onClick = onOpenNotificationList)
                            .padding(ChalkakTheme.spacing.sm),
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
            ) {
                Text(
                    text = uiState.detail.title,
                    modifier = Modifier.padding(top = ChalkakTheme.spacing.xl),
                    color = ChalkakTheme.colors.textPrimary,
                    style = ChalkakTheme.typography.headline,
                )
                Text(
                    text = uiState.detail.body,
                    modifier = Modifier.padding(top = ChalkakTheme.spacing.md),
                    color = ChalkakTheme.colors.textSecondary,
                    style = ChalkakTheme.typography.body,
                )
                uiState.detail.originalImageUrl?.let { imageUrl ->
                    ChalkakImage(
                        model = imageUrl,
                        contentDescription = "반려된 사진",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = notificationOriginalImageMaxHeight)
                            .padding(top = ChalkakTheme.spacing.xl),
                        contentScale = ContentScale.Fit,
                    )
                }
                uiState.detail.rejectionReason?.takeIf(String::isNotBlank)?.let { reason ->
                    Text(
                        text = "반려 사유",
                        modifier = Modifier.padding(top = ChalkakTheme.spacing.xl),
                        color = ChalkakTheme.colors.textPrimary,
                        style = ChalkakTheme.typography.subheadline,
                    )
                    Text(
                        text = reason,
                        modifier = Modifier.padding(top = ChalkakTheme.spacing.sm),
                        color = ChalkakTheme.colors.textSecondary,
                        style = ChalkakTheme.typography.body,
                    )
                }
                Text(
                    text = uiState.detail.createdAt
                        .toNotificationTimeText(),
                    modifier = Modifier.padding(vertical = ChalkakTheme.spacing.xl),
                    color = ChalkakTheme.colors.textInactive,
                    style = ChalkakTheme.typography.caption,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationDetailScreenPreview() {
    ChalkakTheme {
        NotificationDetailScreen(
            uiState = NotificationDetailUiState(isLoading = true),
            onBackClick = {},
            onRetry = {},
            onOpenNotificationList = {},
        )
    }
}

private val notificationOriginalImageMaxHeight = 440.dp
private val notificationDetailTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private val notificationDetailTimeZone = ZoneId.of("Asia/Seoul")

private fun String.toNotificationTimeText(): String = runCatching {
    notificationDetailTimeFormatter.format(Instant.parse(this).atZone(notificationDetailTimeZone))
}.getOrElse { this }
