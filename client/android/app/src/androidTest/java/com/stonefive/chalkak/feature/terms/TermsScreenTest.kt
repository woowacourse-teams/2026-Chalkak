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
    fun termsScreenShowsRequiredAgreementsAndNextButton() {
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
    fun agreeingToAllTermsEnablesNextButton() {
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
    fun nextButtonRequiresAllMandatoryAgreements() {
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
    fun firstViewButtonOpensTermsWithoutChangingAgreementState() {
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
    fun secondViewButtonOpensPrivacyPolicyWithoutChangingAgreementState() {
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
