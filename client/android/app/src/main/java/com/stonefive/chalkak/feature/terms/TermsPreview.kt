package com.stonefive.chalkak.feature.terms

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun TermsScreenPreview() {
    ChalkakTheme {
        TermsRoute(onNextClick = {})
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun TermsScreenAgreedPreview() {
    ChalkakTheme {
        TermsScreen(
            uiState = TermsUiState(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
            ),
            onAction = {},
            onNextClick = {},
        )
    }
}
