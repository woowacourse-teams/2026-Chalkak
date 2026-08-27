package com.stonefive.chalkak.feature.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.auth.GoogleCredentialFailure
import com.stonefive.chalkak.core.auth.GoogleCredentialResult
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.feature.login.component.SocialLoginButton
import kotlinx.coroutines.launch

@Composable
fun LoginRoute(
    onSignUpRequired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val googleIdTokenClient = remember(context) {
        (context.applicationContext as ChalkakApplication).appContainer.googleIdTokenClient
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.status) {
        if (uiState.status == LoginStatus.SignUpRequired) {
            onSignUpRequired()
            viewModel.signUpRequiredHandled()
        }
    }

    LoginScreen(
        onSocialLoginClick = { provider ->
            when (provider) {
                SocialLoginProvider.GOOGLE -> {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        viewModel.credentialRequestFailed("Google 로그인을 시작할 수 없어요.")
                    } else if (viewModel.startCredentialRequest()) {
                        coroutineScope.launch {
                            when (val result = googleIdTokenClient.getIdToken(currentActivity)) {
                                is GoogleCredentialResult.Success -> {
                                    viewModel.login(provider, result.idToken)
                                }

                                GoogleCredentialResult.Cancelled -> {
                                    viewModel.credentialRequestCancelled()
                                }

                                is GoogleCredentialResult.Failure -> {
                                    viewModel.credentialRequestFailed(result.reason.toMessage())
                                }
                            }
                        }
                    }
                }

                SocialLoginProvider.KAKAO -> viewModel.showKakaoPreparing()
            }
        },
        onContinueAsGuestClick = viewModel::continueAsGuest,
        modifier = modifier,
        enabled = uiState.canSubmit,
        errorMessage = uiState.errorMessage,
    )
}

@Composable
fun LoginScreen(
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    onContinueAsGuestClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
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
            errorMessage = errorMessage,
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
    errorMessage: String?,
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

        errorMessage?.let { message ->
            Text(
                text = message,
                color = ChalkakTheme.colors.error,
                style = ChalkakTheme.typography.footnote,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun GoogleCredentialFailure.toMessage(): String = when (this) {
    GoogleCredentialFailure.NO_CREDENTIAL -> "사용 가능한 Google 계정이 없어요."

    GoogleCredentialFailure.INTERRUPTED -> "Google 로그인이 중단됐어요. 다시 시도해 주세요."

    GoogleCredentialFailure.CONFIGURATION -> "Google 로그인 설정을 확인해 주세요."

    GoogleCredentialFailure.UNSUPPORTED -> "이 기기에서는 Google 로그인을 사용할 수 없어요."

    GoogleCredentialFailure.UNEXPECTED_CREDENTIAL,
    GoogleCredentialFailure.INVALID_CREDENTIAL,
    GoogleCredentialFailure.UNKNOWN,
    -> "Google 로그인에 실패했어요. 다시 시도해 주세요."
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
