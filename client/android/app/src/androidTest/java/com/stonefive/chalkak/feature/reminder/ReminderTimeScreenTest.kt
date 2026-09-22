package com.stonefive.chalkak.feature.reminder

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.MainActivity
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReminderTimeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun eveningIsSelectedByDefaultAndAnotherPresetCanBeSelected() {
        var selectedOption = ReminderTimeOption.EVENING

        composeRule.activity.setContent {
            ChalkakTheme {
                ReminderTimeScreen(
                    uiState = ReminderTimeUiState(selectedOption = selectedOption),
                    onOptionClick = { selectedOption = it },
                    onCustomTimeClick = {},
                    onConfirmClick = {},
                    onSkipClick = {},
                )
            }
        }

        composeRule.onNodeWithText("저녁 18:00").assertIsSelected()
        composeRule.onNodeWithText("아침 8:00").performClick()

        assertEquals(ReminderTimeOption.MORNING, selectedOption)
    }

    @Test
    fun confirmAndSkipActionsAreForwarded() {
        var confirmCount = 0
        var skipCount = 0

        composeRule.activity.setContent {
            ChalkakTheme {
                ReminderTimeScreen(
                    uiState = ReminderTimeUiState(),
                    onOptionClick = {},
                    onCustomTimeClick = {},
                    onConfirmClick = { confirmCount++ },
                    onSkipClick = { skipCount++ },
                )
            }
        }

        composeRule.onNodeWithText("이 시간으로 정할게요").performClick()
        composeRule.onNodeWithText("알림 따로 필요 없어요!").performClick()

        assertEquals(1, confirmCount)
        assertEquals(1, skipCount)
    }
}
