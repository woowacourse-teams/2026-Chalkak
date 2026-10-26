package com.stonefive.chalkak.feature.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.pressBack
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feed.component.FeedSignatureHintSheet
import com.stonefive.chalkak.feature.feed.component.SIGNATURE_HINT_MESSAGE
import com.stonefive.chalkak.feature.feed.component.SIGNATURE_HINT_TITLE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedSignatureHintTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheetShowsTitleAndMessageWithoutButtons() {
        composeRule.setContent {
            ChalkakTheme {
                FeedSignatureHintSheet(onDismiss = {})
            }
        }

        composeRule.onNodeWithText(SIGNATURE_HINT_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(SIGNATURE_HINT_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun backPressDismissesTheSheet() {
        var dismissCount = 0
        composeRule.setContent {
            ChalkakTheme {
                FeedSignatureHintSheet(onDismiss = { dismissCount++ })
            }
        }
        composeRule.onNodeWithText(SIGNATURE_HINT_TITLE).assertIsDisplayed()

        pressBack()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }
}
