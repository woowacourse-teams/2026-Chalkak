package com.stonefive.chalkak.feature.today.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.logo.ChalkakLogo
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun TodayTopBar(
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(TodayTopBarHeight)
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                end = ChalkakTheme.spacing.screenHorizontal,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalkakLogo(modifier = Modifier.padding(top = ChalkakTheme.spacing.lg))
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onNotificationClick,
            modifier = Modifier.size(NotificationTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = "알림",
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(NotificationIconSize),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun TodayTopBarPreview() {
    ChalkakTheme {
        TodayTopBar(onNotificationClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

private val TodayTopBarHeight = 55.dp
private val NotificationTouchTarget = 48.dp
private val NotificationIconSize = 24.dp
