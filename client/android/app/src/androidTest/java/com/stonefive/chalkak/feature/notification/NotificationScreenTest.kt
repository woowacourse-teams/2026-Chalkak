package com.stonefive.chalkak.feature.notification

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sampleListDisplaysWithoutNetworkAndBackInvokesCallback() {
        var backCount = 0
        composeRule.setContent {
            ChalkakTheme {
                NotificationScreen(
                    uiState = NotificationUiState(notifications = previewNotifications),
                    onBackClick = { backCount++ },
                )
            }
        }

        composeRule.onNodeWithText("알림").assertIsDisplayed()
        composeRule.onNodeWithText("9월 22일 오늘의 주제를 확인해보세요.").assertIsDisplayed()
        composeRule.onNodeWithText("18:00").assertIsDisplayed()
        composeRule.onNodeWithText("9월 21일 오늘의 주제를 확인해보세요.").assertIsDisplayed()
        composeRule.onNodeWithText("어제 21:10").assertIsDisplayed()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(context.getExternalFilesDir(null), "notification-screen.png")
        screenshot.outputStream().use {
            composeRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun notificationRowsRespondAtBothScreenEdgesAndKeepCallbacksSeparate() {
        val clickedIds = mutableListOf<String>()
        val notifications = listOf(
            NotificationItemUiState(id = "first", title = "첫 번째 알림", timeText = "18:00", isUnread = true),
            NotificationItemUiState(id = "second", title = "두 번째 알림", timeText = "18:01", isUnread = false),
        )
        composeRule.setContent {
            ChalkakTheme {
                NotificationScreen(
                    uiState = NotificationUiState(notifications = notifications, hasUnread = true),
                    onBackClick = {},
                    onNotificationClick = { clickedIds += it.id },
                )
            }
        }

        composeRule.onNodeWithText("모두 읽기").assertDoesNotExist()
        val firstRow = composeRule.onNode(hasClickAction() and hasText("첫 번째 알림"))
        firstRow.performTouchInput { click(Offset(1f, centerY)) }
        firstRow.performTouchInput { click(Offset(width - 1f, centerY)) }
        val secondRow = composeRule.onNode(hasClickAction() and hasText("두 번째 알림"))
        secondRow.performTouchInput { click(Offset(centerX, height - 2f)) }
        composeRule.runOnIdle { assertEquals(listOf("first", "first", "second"), clickedIds) }
    }

    @Test
    fun backButtonRespondsAtTopAndBottomOfItsTouchArea() {
        var backCount = 0
        composeRule.setContent {
            ChalkakTheme {
                NotificationScreen(
                    uiState = NotificationUiState(),
                    onBackClick = { backCount++ },
                )
            }
        }

        val backButton = composeRule.onNodeWithContentDescription("뒤로 가기")
        backButton.performTouchInput { click(Offset(centerX, 1f)) }
        backButton.performTouchInput { click(Offset(centerX, height - 1f)) }
        composeRule.runOnIdle { assertEquals(2, backCount) }
    }

    @Test
    fun notificationListScrollsWhileBackButtonRemainsVisible() {
        val notifications = (1..30).map { index ->
            NotificationItemUiState(
                id = index.toString(),
                title = "알림 $index",
                timeText = "18:00",
                isUnread = false,
            )
        }
        composeRule.setContent {
            ChalkakTheme {
                NotificationScreen(
                    uiState = NotificationUiState(notifications = notifications),
                    onBackClick = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(29)
        composeRule.onNodeWithText("알림 30").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
    }
}
