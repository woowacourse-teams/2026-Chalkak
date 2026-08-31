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
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordScreenRendersCalendarContent() {
        val selectedPhoto = recordPhoto(day = 2)

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(selectedPhoto),
                        selectedDate = selectedPhoto.topicDate,
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
        composeRule.onNodeWithText("8월 2일").assertIsDisplayed()
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
    fun tappingPhotoPassesSelectedDate() {
        val photo = recordPhoto(day = 5)
        var selectedDate: LocalDate? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(photo),
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

        assertEquals(photo.topicDate, selectedDate)
    }

    @Test
    fun monthNavigationInvokesPreviousAndNextCallbacks() {
        var previousMonthClicked = false
        var nextMonthClicked = false

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(recordPhoto(day = 2)),
                        selectedDate = LocalDate.of(2026, 8, 2),
                        latestMonth = RecordTestMonth.plusMonths(1),
                    ),
                    onPreviousMonthClick = { previousMonthClicked = true },
                    onNextMonthClick = { nextMonthClicked = true },
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("이전 달").performClick()
        composeRule.onNodeWithContentDescription("다음 달").performClick()

        assertEquals(true, previousMonthClicked)
        assertEquals(true, nextMonthClicked)
    }

    @Test
    fun recordDestinationIsSelectedInBottomBar() {
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
    fun selectedPhotoOpensInFeed() {
        val photo = recordPhoto(day = 2)
        var openedPostId: String? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(photo),
                        selectedDate = photo.topicDate,
                    ),
                    onPreviousMonthClick = {},
                    onNextMonthClick = {},
                    onDateClick = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = { openedPostId = it },
                )
            }
        }

        composeRule.onNodeWithText("피드에서 보기").performClick()

        assertEquals(photo.postId, openedPostId)
    }

    @Test
    fun selectedPhotoOpensInDisplay() {
        val photo = recordPhoto(day = 2)
        var selectedItem: ChalkakBottomBarItem? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(photo),
                        selectedDate = photo.topicDate,
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
    fun selectedPhotoDateOpensInDisplay() {
        val photo = recordPhoto(day = 2)
        var openedDate: LocalDate? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        posts = listOf(photo),
                        selectedDate = photo.topicDate,
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

        assertEquals(photo.topicDate, openedDate)
    }
}

private val RecordTestMonth = YearMonth.of(2026, 8)

private fun recordPhoto(day: Int): PostCalendarItem = PostCalendarItem(
    postId = "post-$day",
    topicDate = RecordTestMonth.atDay(day),
    thumbnailImageUrl = "android.resource://com.stonefive.chalkak/drawable/home_feed_photo",
    status = PostStatus.APPROVED,
)
