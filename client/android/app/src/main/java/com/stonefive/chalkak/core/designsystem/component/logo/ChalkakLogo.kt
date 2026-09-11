package com.stonefive.chalkak.core.designsystem.component.logo

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakLogo(modifier: Modifier = Modifier) {
    Text(
        text = "Chalkak",
        modifier = modifier,
        color = ChalkakTheme.colors.textPrimary,
        style = ChalkakTheme.typography.brand,
    )
}

@Preview(showBackground = true)
@Composable
private fun ChalkakLogoPreview() {
    ChalkakTheme {
        ChalkakLogo()
    }
}
