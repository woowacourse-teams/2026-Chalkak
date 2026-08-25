package com.stonefive.chalkak.feature.terms

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TermsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `약관 동의 화면의 필수 항목과 다음 버튼이 표시된다`() {
        composeRule.setContent {
            ChalkakTheme {
                TermsRoute(onNextClick = {})
            }
        }

        composeRule
            .onNodeWithText("찰캌에\n오신 것을 환영합니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("전체 동의").assertIsDisplayed()
        composeRule.onNodeWithText("(필수) 서비스 이용약관").assertIsDisplayed()
        composeRule.onNodeWithText("(필수) 개인정보 처리방침").assertIsDisplayed()
        composeRule.onNodeWithText("다음").assertIsNotEnabled()
    }

    @Test
    fun `전체 동의하면 다음 버튼이 활성화된다`() {
        var nextClicked = false
        composeRule.setContent {
            ChalkakTheme {
                TermsRoute(onNextClick = { nextClicked = true })
            }
        }

        composeRule.onNodeWithText("전체 동의").performClick()

        composeRule
            .onNodeWithText("다음")
            .assertIsEnabled()
            .performClick()
        assertTrue(nextClicked)
    }

    @Test
    fun `필수 약관을 모두 동의해야 다음 버튼을 누를 수 있다`() {
        var nextClicked = false
        composeRule.setContent {
            ChalkakTheme {
                TermsRoute(onNextClick = { nextClicked = true })
            }
        }

        composeRule.onNodeWithText("(필수) 서비스 이용약관").performClick()
        composeRule
            .onNodeWithText("다음")
            .assertIsNotEnabled()
            .performClick()
        assertFalse(nextClicked)

        composeRule.onNodeWithText("(필수) 개인정보 처리방침").performClick()
        composeRule
            .onNodeWithText("다음")
            .assertIsEnabled()
            .performClick()
        assertTrue(nextClicked)
    }

    @Test
    fun `첫 번째 보기 버튼은 서비스 이용약관 콜백만 호출하고 동의 상태를 바꾸지 않는다`() {
        var serviceTermsViewCount = 0
        var privacyPolicyViewCount = 0
        composeRule.setContent {
            ChalkakTheme {
                TermsRoute(
                    onNextClick = {},
                    onServiceTermsViewClick = { serviceTermsViewCount++ },
                    onPrivacyPolicyViewClick = { privacyPolicyViewCount++ },
                )
            }
        }

        composeRule
            .onAllNodesWithText("보기")
            .assertCountEquals(2)
            .get(0)
            .performClick()

        assertEquals(1, serviceTermsViewCount)
        assertEquals(0, privacyPolicyViewCount)
        composeRule.onNodeWithText("다음").assertIsNotEnabled()
    }

    @Test
    fun `두 번째 보기 버튼은 개인정보 처리방침 콜백만 호출하고 동의 상태를 바꾸지 않는다`() {
        var serviceTermsViewCount = 0
        var privacyPolicyViewCount = 0
        composeRule.setContent {
            ChalkakTheme {
                TermsRoute(
                    onNextClick = {},
                    onServiceTermsViewClick = { serviceTermsViewCount++ },
                    onPrivacyPolicyViewClick = { privacyPolicyViewCount++ },
                )
            }
        }

        composeRule
            .onAllNodesWithText("보기")
            .assertCountEquals(2)
            .get(1)
            .performClick()

        assertEquals(0, serviceTermsViewCount)
        assertEquals(1, privacyPolicyViewCount)
        composeRule.onNodeWithText("다음").assertIsNotEnabled()
    }
}
