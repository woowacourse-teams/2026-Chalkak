package com.stonefive.chalkak.feature.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `로그인 선택지가 모두 표시된다`() {
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
    fun `구글 버튼을 누르면 구글 제공자를 전달한다`() {
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
}
