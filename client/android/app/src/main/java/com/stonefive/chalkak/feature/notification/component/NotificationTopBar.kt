package com.stonefive.chalkak.feature.notification.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun NotificationTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "알림",
    onMarkAllReadClick: (() -> Unit)? = null,
    isMarkAllReadEnabled: Boolean = false,
) {
    Box(modifier = modifier.height(notificationTopBarHeight)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(notificationTopBarActionSize)
                .fillMaxHeight()
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(notificationBackIconSize),
            )
        }

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = notificationTopBarActionSize),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            textAlign = TextAlign.Center,
        )

        if (onMarkAllReadClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // 글자 끝은 화면 여백에 맞추고 터치 영역만 글자 주변으로 좁힌다.
                    .padding(end = ChalkakTheme.spacing.screenHorizontal - ChalkakTheme.spacing.sm)
                    .heightIn(min = notificationMarkAllReadMinHeight)
                    .clickable(
                        enabled = isMarkAllReadEnabled,
                        role = Role.Button,
                        onClick = onMarkAllReadClick,
                    ).padding(horizontal = ChalkakTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "모두 읽음",
                    color = if (isMarkAllReadEnabled) {
                        ChalkakTheme.colors.textPrimary
                    } else {
                        ChalkakTheme.colors.textInactive
                    },
                    style = ChalkakTheme.typography.subheadline,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun NotificationTopBarPreview() {
    ChalkakTheme {
        NotificationTopBar(
            onBackClick = {},
            modifier = Modifier.fillMaxWidth(),
            onMarkAllReadClick = {},
            isMarkAllReadEnabled = true,
        )
    }
}

private val notificationTopBarHeight = 72.dp
private val notificationTopBarActionSize = 56.dp
private val notificationBackIconSize = 28.dp
private val notificationMarkAllReadMinHeight = 48.dp
