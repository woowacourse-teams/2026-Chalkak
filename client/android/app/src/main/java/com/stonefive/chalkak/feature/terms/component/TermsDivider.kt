package com.stonefive.chalkak.feature.terms.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
internal fun TermsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChalkakTheme.colors.border),
    )
}

@Preview(showBackground = true, widthDp = 352)
@Composable
private fun TermsDividerPreview() {
    ChalkakTheme {
        TermsDivider()
    }
}
