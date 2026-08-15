package com.stonefive.chalkak.feature.signature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignaturePreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsSignedPhotoAndGuideText() {
        composeRule.setContent {
            ChalkakTheme {
                SignaturePreviewScreen(
                    imageModel = R.drawable.preview_photo,
                    signatureModel = R.drawable.preview_signature,
                    onRedrawClick = {},
                    onStartClick = {},
                )
            }
        }

        composeRule.onNodeWithText("이렇게 보여요").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("사진에 사인이 적용된 모습")
            .assertIsDisplayed()
    }

    @Test
    fun invokesCallbacksWhenBottomButtonsAreClicked() {
        var redrawClicked = false
        var startClicked = false

        composeRule.setContent {
            ChalkakTheme {
                SignaturePreviewScreen(
                    imageModel = R.drawable.preview_photo,
                    signatureModel = R.drawable.preview_signature,
                    onRedrawClick = { redrawClicked = true },
                    onStartClick = { startClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("다시 그리기").performClick()
        composeRule.onNodeWithText("시작하기").performClick()

        assertTrue(redrawClicked)
        assertTrue(startClicked)
    }
}
