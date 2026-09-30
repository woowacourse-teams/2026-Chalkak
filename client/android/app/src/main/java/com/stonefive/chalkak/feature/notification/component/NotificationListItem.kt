package com.stonefive.chalkak.feature.notification.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.notification.NotificationItemUiState
import com.stonefive.chalkak.feature.notification.previewNotifications

@Composable
fun NotificationListItem(
    item: NotificationItemUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .semantics {
                stateDescription = if (item.isUnread) {
                    "읽지 않음"
                } else {
                    "읽음"
                }
            }.padding(vertical = ChalkakTheme.spacing.xl),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(ChalkakTheme.spacing.md)
                .padding(top = ChalkakTheme.spacing.xs),
        ) {
            if (item.isUnread) {
                Box(
                    modifier = Modifier
                        .size(ChalkakTheme.spacing.sm)
                        .background(
                            color = ChalkakTheme.colors.inputCursor,
                            shape = ChalkakTheme.shapes.pill,
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = ChalkakTheme.spacing.md)
                .weight(1f),
        ) {
            Text(
                text = item.title,
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.subheadline,
                maxLines = NOTIFICATION_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = item.timeText,
                modifier = Modifier.padding(top = ChalkakTheme.spacing.md),
                color = ChalkakTheme.colors.textInactive,
                style = ChalkakTheme.typography.caption,
            )
        }

        item.thumbnailModel?.let { thumbnail ->
            ChalkakImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = ChalkakTheme.spacing.lg)
                    .size(notificationThumbnailSize),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun UnreadNotificationListItemPreview() {
    ChalkakTheme {
        NotificationListItem(
            item = previewNotifications.first(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        )
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun ReadLongNotificationListItemPreview() {
    ChalkakTheme {
        NotificationListItem(
            item = NotificationItemUiState(
                id = "long-read",
                title = "오늘의 주제를 확인하고 사진으로 기록을 남겨보세요. 긴 문장은 두 줄에서 말줄임 처리돼요.",
                timeText = "9월 20일 18:00",
                isUnread = false,
                thumbnailModel = R.drawable.preview_photo,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        )
    }
}

private val notificationThumbnailSize = 44.dp
private const val NOTIFICATION_TITLE_MAX_LINES = 2
