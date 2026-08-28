package com.stonefive.chalkak.feature.signature

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingSignatureRoute(
    onPreviewRequested: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureViewModel = viewModel(
        factory = SignatureViewModel.Factory,
    ),
) {
    SignatureEditorRoute(
        onSignatureSaved = onPreviewRequested,
        modifier = modifier,
        viewModel = viewModel,
    )
}

@Composable
fun ChangeSignatureRoute(
    onPreviewRequested: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureViewModel = viewModel(
        factory = SignatureViewModel.Factory,
    ),
) {
    SignatureEditorRoute(
        onSignatureSaved = onPreviewRequested,
        modifier = modifier,
        viewModel = viewModel,
    )
}
