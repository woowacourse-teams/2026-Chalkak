package com.stonefive.chalkak.feature.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allLoginOptionsAreDisplayed() {
        composeRule.setContent {
            ChalkakTheme {
                LoginScreen(
                    onSocialLoginClick = {},
                    onContinueAsGuestClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Google로 계속하기").assertIsDisplayed()
        composeRule.onNodeWithText("카카오로 계속하기").assertIsDisplayed()
        composeRule.onNodeWithText("로그인 없이 사진 둘러보기").assertIsDisplayed()
    }

    @Test
    fun tappingGoogleButtonPassesGoogleProvider() {
        var selectedProvider: SocialLoginProvider? = null
        composeRule.setContent {
            ChalkakTheme {
                LoginScreen(
                    onSocialLoginClick = { selectedProvider = it },
                    onContinueAsGuestClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Google로 계속하기").performClick()

        assertEquals(SocialLoginProvider.GOOGLE, selectedProvider)
    }

    @Test
    fun tappingKakaoButtonPassesKakaoProvider() {
        var selectedProvider: SocialLoginProvider? = null
        composeRule.setContent {
            ChalkakTheme {
                LoginScreen(
                    onSocialLoginClick = { selectedProvider = it },
                    onContinueAsGuestClick = {},
                )
            }
        }

        composeRule.onNodeWithText("카카오로 계속하기").performClick()

        assertEquals(SocialLoginProvider.KAKAO, selectedProvider)
    }

    @Test
    fun successfulLoginInvokesLoginSuccessCallback() {
        var loginSuccessCount = 0
        val viewModel = LoginViewModel(FakeLoginRepository())
        composeRule.setContent {
            ChalkakTheme {
                LoginRoute(
                    onLoginSuccess = { loginSuccessCount++ },
                    onGuestAccessGranted = {},
                    onSignUpRequired = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.runOnIdle {
            viewModel.login(SocialLoginProvider.GOOGLE, "test-id-token", "test-nonce")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { loginSuccessCount == 1 }
        composeRule.waitForIdle()
        assertEquals(1, loginSuccessCount)
    }

    @Test
    fun tappingGuestOptionInvokesGuestAccessCallback() {
        var guestAccessGranted = false
        val viewModel = LoginViewModel(FakeLoginRepository())

        composeRule.setContent {
            ChalkakTheme {
                LoginRoute(
                    onLoginSuccess = {},
                    onGuestAccessGranted = { guestAccessGranted = true },
                    onSignUpRequired = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("로그인 없이 사진 둘러보기").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { guestAccessGranted }

        assertTrue(guestAccessGranted)
    }
}

private class FakeLoginRepository : AuthRepository {
    override val sessionState = MutableStateFlow<UserSessionState>(UserSessionState.SignedOut)

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
        rawNonce: String,
    ): SocialLoginResult {
        sessionState.value = UserSessionState.Authenticated("test-user")
        return SocialLoginResult.LoginSuccess("test-user")
    }

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult = error("Not used")

    override suspend fun continueAsGuest() {
        sessionState.value = UserSessionState.Guest
    }

    override suspend fun logout() = Unit
}
