package com.stonefive.chalkak.feature.upload

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoUploadSuccessScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successScreenUsesSubmissionDataWithoutMockProfileCopy() {
        composeRule.setContent {
            ChalkakTheme {
                PhotoUploadSuccessScreen(
                    imageModel = R.drawable.preview_photo,
                    caption = "한낮의 다리",
                    content = PhotoUploadSuccessContent(
                        date = LocalDate.of(2026, 8, 29),
                        topic = "바다",
                        moderationStatus = "PENDING",
                    ),
                    onConfirmClick = {},
                )
            }
        }

        composeRule.onNodeWithText("2026. 08. 29").assertIsDisplayed()
        composeRule.onNodeWithText("한낮의 다리").assertIsDisplayed()
        composeRule.onNodeWithText("‘바다’를 기록했어요.").assertIsDisplayed()
        composeRule
            .onNodeWithText("검수를 기다리고 있어요. 피드 표시까지 시간이 조금 걸릴 수도 있어요!")
            .assertIsDisplayed()
        assertEquals(
            0,
            composeRule
                .onAllNodesWithText("번째 전시")
                .fetchSemanticsNodes()
                .size,
        )
        assertTrue(
            composeRule
                .onAllNodesWithText("님")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
