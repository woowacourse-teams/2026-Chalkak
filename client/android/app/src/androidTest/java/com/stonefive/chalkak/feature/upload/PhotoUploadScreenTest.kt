package com.stonefive.chalkak.feature.upload

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoUploadScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submitButtonIsDisabledWithoutPhoto() {
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("전시하기").assertIsDisplayed()
        composeRule.onNodeWithText("주제 ‘틈’에 맞는 한 장").assertIsDisplayed()
        composeRule.onNodeWithTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun submitButtonIsEnabledWithPhoto() {
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(selectedImage = PREVIEW_PHOTO_URI),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun photoSourceAndBackActionsAreForwarded() {
        val actions = mutableListOf<PhotoUploadUiAction>()
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("앨범에서 사진 선택").performClick()
        composeRule.onNodeWithContentDescription("카메라로 촬영").performClick()
        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()

        assertTrue(actions.contains(PhotoUploadUiAction.GalleryClicked))
        assertTrue(actions.contains(PhotoUploadUiAction.CameraClicked))
        assertTrue(actions.contains(PhotoUploadUiAction.BackClicked))
    }

    @Test
    fun cameraActionIsHiddenWithoutCamera() {
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(isCameraAvailable = false),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("앨범에서 사진 선택").assertIsDisplayed()
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription("카메라로 촬영")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun submitActionIsForwarded() {
        val actions = mutableListOf<PhotoUploadUiAction>()
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(selectedImage = PREVIEW_PHOTO_URI),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG).performClick()

        assertTrue(actions.contains(PhotoUploadUiAction.SubmitClicked))
    }

    @Test
    fun captionCanScrollIntoViewInCompactHeight() {
        composeRule.setContent {
            ChalkakTheme {
                Box(modifier = Modifier.size(width = 390.dp, height = 400.dp)) {
                    PhotoUploadScreen(
                        uiState = PhotoUploadUiState(selectedImage = PREVIEW_PHOTO_URI),
                        onAction = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(PHOTO_UPLOAD_CAPTION_TAG)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(PHOTO_UPLOAD_SUBMIT_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun tappingOutsideCaptionClearsFocus() {
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadScreen(
                    uiState = PhotoUploadUiState(),
                    onAction = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(PHOTO_UPLOAD_CAPTION_TAG)
            .performClick()
            .assertIsFocused()

        composeRule.onNodeWithText("주제 ‘틈’에 맞는 한 장").performTouchInput {
            click()
        }

        composeRule.onNodeWithTag(PHOTO_UPLOAD_CAPTION_TAG).assertIsNotFocused()
    }
}

private val PREVIEW_PHOTO_URI = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}"
