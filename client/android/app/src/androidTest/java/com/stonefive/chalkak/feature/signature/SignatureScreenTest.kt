package com.stonefive.chalkak.feature.signature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignatureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submitButtonIsDisabledWithoutSignature() {
        composeRule.setContent {
            ChalkakTheme {
                SignatureScreen(
                    uiState = SignatureUiState(),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("signatureSubmitButton").assertIsNotEnabled()
    }

    @Test
    fun submitButtonIsEnabledWithSignature() {
        composeRule.setContent {
            ChalkakTheme {
                SignatureScreen(
                    uiState = SignatureUiState(
                        strokes = listOf(
                            SignatureStroke(
                                points = listOf(SignaturePoint(0.2f, 0.2f)),
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("signatureSubmitButton").assertIsEnabled()
    }

    @Test
    fun draggingSignaturePadForwardsStrokeAction() {
        val actions = mutableListOf<SignatureUiAction>()
        composeRule.setContent {
            ChalkakTheme {
                SignatureScreen(
                    uiState = SignatureUiState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag("signaturePad").performTouchInput {
            swipe(
                start = Offset(width * 0.2f, height * 0.3f),
                end = Offset(width * 0.8f, height * 0.7f),
                durationMillis = 300,
            )
        }

        assertTrue(actions.any { it is SignatureUiAction.StrokeStarted })
        assertTrue(actions.any { it is SignatureUiAction.StrokeMoved })
        assertTrue(actions.any { it is SignatureUiAction.StrokeFinished })
    }

    @Test
    fun tappingSignaturePadForwardsSinglePointStroke() {
        val actions = mutableListOf<SignatureUiAction>()
        composeRule.setContent {
            ChalkakTheme {
                SignatureScreen(
                    uiState = SignatureUiState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag("signaturePad").performTouchInput {
            click(center)
        }

        assertTrue(actions.any { it is SignatureUiAction.StrokeStarted })
        assertTrue(actions.any { it is SignatureUiAction.StrokeFinished })
    }
}
