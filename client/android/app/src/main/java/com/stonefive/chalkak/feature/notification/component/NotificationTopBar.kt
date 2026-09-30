package com.stonefive.chalkak.feature.notification.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
) {
    Row(
        modifier = modifier.height(notificationTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(notificationTopBarActionSize)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(onClick = onBackClick)
                .padding(ChalkakTheme.spacing.sm),
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
            text = "알림",
            modifier = Modifier.weight(1f),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.headline,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.size(notificationTopBarActionSize))
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun NotificationTopBarPreview() {
    ChalkakTheme {
        NotificationTopBar(
            onBackClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val notificationTopBarHeight = 72.dp
private val notificationTopBarActionSize = 56.dp
private val notificationBackIconSize = 28.dp
