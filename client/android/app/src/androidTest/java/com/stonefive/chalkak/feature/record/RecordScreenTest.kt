package com.stonefive.chalkak.feature.record

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.RecordPhoto
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCalendarContent() {
        val selectedPhoto = recordPhoto(day = 2)

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        isLoading = false,
                        photos = listOf(selectedPhoto),
                        selectedDate = selectedPhoto.date,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                )
            }
        }

        composeRule.onNodeWithText("2026년 8월").assertIsDisplayed()
        composeRule.onNodeWithText("일").assertIsDisplayed()
        composeRule.onNodeWithText("이미지로 저장").assertIsDisplayed()
        composeRule.onNodeWithText("피드에서 보기").assertIsDisplayed()
        composeRule.onNodeWithText("전시 보러가기").assertIsDisplayed()
        composeRule.onNodeWithText("8월 2일 · 물결").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("2026년 8월 2일 사진")
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithContentDescription("2026년 8월 5일 사진 없음")
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithContentDescription("달력 빈 칸")
            .assertCountEquals(0)
    }

    @Test
    fun passesClickedPhotoDate() {
        val photo = recordPhoto(day = 5)
        var selectedDate: LocalDate? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        isLoading = false,
                        photos = listOf(photo),
                        selectedDate = null,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = { selectedDate = it },
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("2026년 8월 5일 사진").performClick()

        assertEquals(photo.date, selectedDate)
    }

    @Test
    fun showsRecordBottomBarSelection() {
        var selectedItem: ChalkakBottomBarItem? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(isLoading = false),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = { selectedItem = it },
                )
            }
        }

        composeRule.onNodeWithText("기록").assertIsDisplayed()
        composeRule.onNodeWithText("오늘").performClick()

        assertEquals(ChalkakBottomBarItem.TODAY, selectedItem)
    }

    @Test
    fun opensSelectedPhotoInFeed() {
        val photo = recordPhoto(day = 2)
        var openedPhoto: RecordPhoto? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        isLoading = false,
                        photos = listOf(photo),
                        selectedDate = photo.date,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = { openedPhoto = it },
                )
            }
        }

        composeRule.onNodeWithText("피드에서 보기").performClick()

        assertEquals(photo, openedPhoto)
    }

    @Test
    fun opensSelectedPhotoInDisplay() {
        val photo = recordPhoto(day = 2)
        var selectedItem: ChalkakBottomBarItem? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        isLoading = false,
                        photos = listOf(photo),
                        selectedDate = photo.date,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = { selectedItem = it },
                )
            }
        }

        composeRule.onNodeWithText("전시 보러가기").performClick()

        assertEquals(ChalkakBottomBarItem.DISPLAY, selectedItem)
    }

    @Test
    fun opensSelectedPhotoDateInDisplay() {
        val photo = recordPhoto(day = 2)
        var openedDate: LocalDate? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        isLoading = false,
                        photos = listOf(photo),
                        selectedDate = photo.date,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenDisplay = { openedDate = it },
                )
            }
        }

        composeRule.onNodeWithText("전시 보러가기").performClick()

        assertEquals(photo.date, openedDate)
    }
}

private fun recordPhoto(day: Int): RecordPhoto = RecordPhoto(
    date = LocalDate.of(2026, 8, day),
    imageUrl = "android.resource://com.stonefive.chalkak/drawable/home_feed_photo",
    signatureUrl = "android.resource://com.stonefive.chalkak/drawable/preview_signature",
    contentDescription = "노을과 전신주",
    title = "물결",
)
