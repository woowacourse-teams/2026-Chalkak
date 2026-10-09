package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.logo.ChalkakLogo
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun HomeTopBar(
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalkakLogo()
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(NotificationTouchTargetSize)
                .semantics { contentDescription = "알림" }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onNotificationClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(NotificationIconSize),
            )
        }
    }
}

val HomeTopBarHeight = 55.dp

private val NotificationTouchTargetSize = 40.dp
private val NotificationIconSize = 22.dp

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun HomeTopBarPreview() {
    ChalkakTheme {
        HomeTopBar(
            onNotificationClick = {
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    HomeTopBarHeight,
                ).padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        )
    }
}
