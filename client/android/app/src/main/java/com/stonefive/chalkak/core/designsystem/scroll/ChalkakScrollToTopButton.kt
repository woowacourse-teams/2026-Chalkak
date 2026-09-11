package com.stonefive.chalkak.core.designsystem.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite

@Composable
fun ChalkakScrollToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = ChalkakWhite,
        shadowElevation = 12.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "맨 위로",
                tint = ChalkakTheme.colors.iconPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = 90f },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakScrollToTopButtonPreview() {
    ChalkakTheme {
        ChalkakScrollToTopButton(onClick = {})
    }
}
