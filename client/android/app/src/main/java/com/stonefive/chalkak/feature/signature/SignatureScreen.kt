package com.stonefive.chalkak.feature.signature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.signature.component.SignatureControlButton
import com.stonefive.chalkak.feature.signature.component.SignaturePad

@Composable
fun SignatureEditorRoute(
    onSignatureSaved: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureViewModel = viewModel(factory = SignatureViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SignatureUiEvent.SignatureSaved -> onSignatureSaved(event.signaturePng)
            }
        }
    }

    SignatureScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun SignatureScreen(
    uiState: SignatureUiState,
    onAction: (SignatureUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .systemBarsPadding()
            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "작가님의\n사인을 그려주세요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "모든 사진에 함께할 사인이에요.\n자유롭게 남겨주시고, 실제 서명은 피해 주세요.",
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.subheadline,
        )

        Spacer(modifier = Modifier.height(32.dp))

        SignaturePad(
            strokes = uiState.strokes,
            enabled = !uiState.isSubmitting,
            onStrokeStarted = { onAction(SignatureUiAction.StrokeStarted(it)) },
            onStrokeMoved = { onAction(SignatureUiAction.StrokeMoved(it)) },
            onStrokeFinished = { onAction(SignatureUiAction.StrokeFinished) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            SignatureControlButton(
                text = "되돌리기",
                enabled = uiState.hasSignature && !uiState.isSubmitting,
                onClick = { onAction(SignatureUiAction.UndoClicked) },
            )

            SignatureControlButton(
                text = "전체 지우기",
                enabled = uiState.hasSignature && !uiState.isSubmitting,
                onClick = { onAction(SignatureUiAction.ClearClicked) },
            )
        }

        uiState.error?.let {
            Text(
                text = "사인을 저장하지 못했어요. 다시 시도해 주세요.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ChalkakTheme.spacing.md),
                color = ChalkakTheme.colors.error,
                style = ChalkakTheme.typography.footnote,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ChalkakButton(
            text = "이 사인으로 할래요",
            onClick = { onAction(SignatureUiAction.SubmitClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SUBMIT_BUTTON_TAG),
            enabled = uiState.canSubmit,
        )

        Spacer(modifier = Modifier.height(18.dp))
    }
}

private const val SUBMIT_BUTTON_TAG = "signatureSubmitButton"

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun SignatureScreenPreview() {
    ChalkakTheme {
        SignatureScreen(
            uiState = SignatureUiState(),
            onAction = {},
        )
    }
}
