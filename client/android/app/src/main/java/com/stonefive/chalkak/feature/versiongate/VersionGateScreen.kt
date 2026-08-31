package com.stonefive.chalkak.feature.versiongate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun VersionGateLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .safeDrawingPadding(),
    )
}

@Composable
fun VersionCheckFailureScreen(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .safeDrawingPadding()
            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "업데이트를 확인할 수 없어요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "네트워크 연결을 확인한 후 다시 시도해 주세요.",
            modifier = Modifier.padding(top = ChalkakTheme.spacing.sm),
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.callout,
            textAlign = TextAlign.Center,
        )
        ChalkakButton(
            text = "다시 시도",
            onClick = onRetryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun VersionGateLoadingScreenPreview() {
    ChalkakTheme {
        VersionGateLoadingScreen()
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun VersionCheckFailureScreenPreview() {
    ChalkakTheme {
        VersionCheckFailureScreen(onRetryClick = {})
    }
}
