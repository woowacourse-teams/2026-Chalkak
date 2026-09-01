package com.stonefive.chalkak.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.feature.login.component.SocialLoginButton

@Composable
fun LoginRoute(
    onSignUpRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UiMessageEffect(uiState.pendingMessage, viewModel::onMessageShown)
    val loginWithGoogle = rememberGoogleLoginAction(
        onStart = { viewModel.startCredentialRequest(SocialLoginProvider.GOOGLE) },
        onSuccess = { idToken ->
            viewModel.login(SocialLoginProvider.GOOGLE, idToken)
        },
        onCancelled = viewModel::credentialRequestCancelled,
        onFailure = viewModel::credentialRequestFailed,
    )
    val loginWithKakao = rememberKakaoLoginAction(
        onStart = { viewModel.startCredentialRequest(SocialLoginProvider.KAKAO) },
        onSuccess = { idToken ->
            viewModel.login(SocialLoginProvider.KAKAO, idToken)
        },
        onCancelled = viewModel::credentialRequestCancelled,
        onFailure = viewModel::credentialRequestFailed,
    )

    LaunchedEffect(uiState.status) {
        if (uiState.status == LoginStatus.SignUpRequired) {
            onSignUpRequired()
            viewModel.signUpRequiredHandled()
        }
    }

    LoginScreen(
        onSocialLoginClick = { provider ->
            when (provider) {
                SocialLoginProvider.GOOGLE -> loginWithGoogle()
                SocialLoginProvider.KAKAO -> loginWithKakao()
            }
        },
        onContinueAsGuestClick = viewModel::continueAsGuest,
        modifier = modifier,
        enabled = uiState.canSubmit,
    )
}

@Composable
fun LoginScreen(
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    onContinueAsGuestClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background),
    ) {
        LoginHeader(modifier = Modifier.weight(1.2f))

        LoginActions(
            onSocialLoginClick = onSocialLoginClick,
            onContinueAsGuestClick = onContinueAsGuestClick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoginHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        ChalkakImage(
            model = R.drawable.img_login_background,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.2f),
            contentScale = ContentScale.Crop,
        )

        Text(
            text = "매일 하나의 주제,\n각자의 한 장",
            modifier = Modifier
                .statusBarsPadding()
                .padding(
                    start = ChalkakTheme.spacing.screenHorizontal,
                    top = 110.dp,
                ),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.display,
        )
    }
}

@Composable
private fun LoginActions(
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    onContinueAsGuestClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val providers = remember { SocialLoginProvider.entries }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = ChalkakTheme.spacing.screenHorizontal,
                vertical = ChalkakTheme.spacing.xxl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
    ) {
        providers.forEach { provider ->
            SocialLoginButton(
                provider = provider,
                onClick = { onSocialLoginClick(provider) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }

        Text(
            text = "로그인 없이 사진 둘러보기",
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onContinueAsGuestClick)
                .padding(ChalkakTheme.spacing.sm),
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.subheadline,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    ChalkakTheme {
        LoginScreen(
            onSocialLoginClick = {},
            onContinueAsGuestClick = {},
        )
    }
}
