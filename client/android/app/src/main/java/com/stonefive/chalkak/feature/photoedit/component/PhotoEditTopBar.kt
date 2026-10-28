package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun PhotoEditTopBar(
    onCancelClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoEditTopBarAction(text = "취소", onClick = onCancelClick)
        PhotoEditTopBarAction(text = "완료", onClick = onDoneClick)
    }
}

@Composable
private fun PhotoEditTopBarAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ChalkakTheme.spacing.md, vertical = ChalkakTheme.spacing.sm),
        style = ChalkakTheme.typography.body,
        color = ChalkakTheme.colors.photoEditContent,
    )
}

@Preview(widthDp = 402)
@Composable
private fun PhotoEditTopBarPreview() {
    ChalkakTheme {
        PhotoEditTopBar(
            onCancelClick = {},
            onDoneClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground),
        )
    }
}
