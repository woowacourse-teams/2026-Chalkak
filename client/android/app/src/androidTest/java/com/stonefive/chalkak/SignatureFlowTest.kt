package com.stonefive.chalkak

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.feature.signature.SignUpViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Terms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignatureFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigatesFromTermsThroughSignaturePreviewToHomeAndDisplay() {
        val signUpRepository = FakeSignUpRepository()
        val signUpViewModel = SignUpViewModel(signUpRepository)

        composeRule.activity.setContent {
            ChalkakTheme {
                ChalkakNavHost(
                    startDestination = Terms,
                    signUpViewModel = signUpViewModel,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("전체 동의")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("전체 동의").performClick()
        composeRule
            .onNodeWithText("다음")
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("작가님의\n사인을 그려주세요")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("signaturePad").performTouchInput {
            swipe(
                start = Offset(width * 0.2f, height * 0.3f),
                end = Offset(width * 0.8f, height * 0.7f),
                durationMillis = 300,
            )
        }
        composeRule
            .onNodeWithTag("signatureSubmitButton")
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("이렇게 보여요")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithText("시작하기")
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("하늘하늘하늘")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("하늘하늘하늘").assertIsDisplayed()

        composeRule.onNodeWithText("전시").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("바다")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        assertTrue(signUpRepository.completedSignaturePng.isNotEmpty())
    }
}

private class FakeSignUpRepository : AuthRepository {
    override val sessionState: StateFlow<UserSessionState> =
        MutableStateFlow(UserSessionState.SignedOut)
    var completedSignaturePng = ByteArray(0)

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult {
        completedSignaturePng = signaturePng
        return SocialSignUpResult.Success("user-id")
    }

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit

    override suspend fun withdraw() = Unit
}
