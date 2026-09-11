package com.stonefive.chalkak.feature.versiongate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VersionGateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun updateCheckFailureShowsRetryScreenInsteadOfAppContent() {
        var retryClicked = false
        composeRule.setContent {
            ChalkakTheme {
                VersionCheckFailureScreen(onRetryClick = { retryClicked = true })
            }
        }

        composeRule.onNodeWithText("업데이트를 확인할 수 없어요").assertIsDisplayed()
        composeRule
            .onNodeWithText("다시 시도")
            .assertIsDisplayed()
            .performClick()

        assertTrue(retryClicked)
    }
}
