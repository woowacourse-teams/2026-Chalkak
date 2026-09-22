package com.stonefive.chalkak

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.feature.signature.SignUpViewModel
import com.stonefive.chalkak.navigation.ChalkakNavHost
import com.stonefive.chalkak.navigation.Display
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
        grantNotificationPermission()
        val signUpRepository = FakeSignUpRepository()
        val signUpViewModel = SignUpViewModel(signUpRepository)
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            ChalkakTheme {
                navController = rememberNavController()
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    navController = navController,
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
                .onAllNodesWithText("언제 알려드릴까요?")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithText("이 시간으로 정할게요")
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("오늘")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("오늘").assertIsDisplayed()

        composeRule.onNodeWithText("전시").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        assertTrue(signUpRepository.completedSignaturePng.isNotEmpty())
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            composeRule.activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(
                composeRule.activity.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
    }
}

private class FakeSignUpRepository : AuthRepository {
    override val sessionState: StateFlow<UserSessionState> =
        MutableStateFlow(UserSessionState.SignedOut)
    var completedSignaturePng = ByteArray(0)

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
        rawNonce: String,
    ): SocialLoginResult = error("Not used")

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult {
        completedSignaturePng = signaturePng
        return SocialSignUpResult.Success("user-id")
    }

    override suspend fun continueAsGuest() = Unit

    override suspend fun logout() = Unit
}

private object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackScreenView(
        screenName: String,
        screenClass: String,
    ) = Unit

    override fun trackBottomNavigationSelection(destination: String) = Unit
}
