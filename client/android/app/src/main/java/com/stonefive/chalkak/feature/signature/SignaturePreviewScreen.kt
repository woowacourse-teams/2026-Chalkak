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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.UserProfile

private val PreviewSignatureWidth = 112.dp
private val PreviewSignatureHeight = 84.dp

@Composable
fun OnboardingSignaturePreviewRoute(
    imageModel: Any?,
    signaturePng: ByteArray,
    onRedrawClick: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onReauthenticationRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            SignUpStatus.Completed -> onSignUpSuccess()
            SignUpStatus.ReauthenticationRequired -> onReauthenticationRequired()
            else -> Unit
        }
    }

    SignaturePreviewScreen(
        imageModel = imageModel,
        signatureModel = signaturePng,
        onRedrawClick = onRedrawClick,
        onStartClick = { viewModel.completeSignUp(signaturePng) },
        modifier = modifier,
        isSubmitting = uiState.isSubmitting,
    )
}

@Composable
fun ChangeSignaturePreviewRoute(
    signaturePng: ByteArray,
    onSignatureChanged: (UserProfile) -> Unit,
    onRedrawClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignatureChangeViewModel = viewModel(factory = SignatureChangeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)

    LaunchedEffect(uiState.status) {
        val status = uiState.status
        if (status is SignatureChangeStatus.Completed) {
            onSignatureChanged(status.profile)
        }
    }

    SignaturePreviewScreen(
        imageModel = R.drawable.preview_photo,
        signatureModel = signaturePng,
        onRedrawClick = onRedrawClick,
        onStartClick = { viewModel.updateSignature(signaturePng) },
        modifier = modifier,
        isSubmitting = uiState.isSubmitting,
        confirmText = "사인 변경하기",
        noticeText = "사인 변경까지 시간이 조금 걸릴 수 있어요",
    )
}

@Composable
fun SignaturePreviewScreen(
    imageModel: Any?,
    signatureModel: Any,
    onRedrawClick: () -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    confirmText: String = "시작하기",
    noticeText: String? = null,
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
            text = "이렇게 보여요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )

        noticeText?.let { text ->
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ChalkakTheme.spacing.sm),
                color = ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.subheadline,
            )
        }

        Spacer(modifier = Modifier.height(50.dp))

        ChalkakSignedImage(
            imageModel = imageModel,
            signatureModel = signatureModel,
            contentDescription = "사진에 사인이 적용된 모습",
            signatureModifier = Modifier.size(
                width = PreviewSignatureWidth,
                height = PreviewSignatureHeight,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 6f)
                .clip(ChalkakTheme.shapes.xlarge),
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChalkakOutlinedButton(
                text = "다시 그리기",
                onClick = onRedrawClick,
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting,
            )

            ChalkakButton(
                text = confirmText,
                onClick = onStartClick,
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun SignaturePreviewScreenPreview() {
    ChalkakTheme {
        SignaturePreviewScreen(
            imageModel = R.drawable.preview_photo,
            signatureModel = R.drawable.preview_signature,
            onRedrawClick = {},
            onStartClick = {},
        )
    }
}
