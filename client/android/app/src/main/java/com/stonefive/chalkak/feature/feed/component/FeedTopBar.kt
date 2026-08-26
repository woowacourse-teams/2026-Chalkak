package com.stonefive.chalkak.feature.feed.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FeedTopBar(
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    isDeleteVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "뒤로 가기" }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onNavigateBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (isDeleteVisible) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "삭제" }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onDeleteClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "삭제",
                    color = ChalkakTheme.colors.destructive,
                    style = ChalkakTheme.typography.callout,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun FeedTopBarPreview() {
    ChalkakTheme {
        FeedTopBar(
            onNavigateBack = {},
            onDeleteClick = {},
            isDeleteVisible = true,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 4.dp,
                    top = 26.dp,
                    end = 12.dp,
                    bottom = 26.dp,
                ),
        )
    }
}
