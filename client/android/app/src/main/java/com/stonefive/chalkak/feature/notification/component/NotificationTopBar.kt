package com.stonefive.chalkak.feature.notification.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
                .align(Alignment.TopStart)
                .padding(start = notificationBackButtonStart, top = notificationTopBarContentTop)
                .size(notificationTopBarTouchSize)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(notificationBackIconSize),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = notificationTopBarContentTop,
                    start = notificationTitleSideInset,
                    end = notificationTitleSideInset,
                ).height(notificationTopBarTouchSize),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.headline,
                textAlign = TextAlign.Center,
            )
        }

        if (onMarkAllReadClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 글자 끝은 화면 여백에 맞추고 터치 영역만 글자 주변으로 좁힌다.
                    .padding(
                        top = notificationTopBarContentTop,
                        end = ChalkakTheme.spacing.screenHorizontal - ChalkakTheme.spacing.sm,
                    ).height(notificationTopBarTouchSize)
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

// 피드·사진 업로드·고객센터 탑바와 같은 값: 위 10dp, 터치 영역 44dp, 아래 8dp, 아이콘 24dp, 왼쪽 8dp.
private val notificationTopBarContentTop = 10.dp
private val notificationTopBarTouchSize = 44.dp
private val notificationTopBarHeight = 62.dp
private val notificationBackButtonStart = 8.dp
private val notificationBackIconSize = 24.dp

// 제목이 왼쪽 뒤로 가기 영역(8 + 44dp)과 겹치지 않게 양쪽을 같은 폭만큼 비운다.
private val notificationTitleSideInset = 52.dp
