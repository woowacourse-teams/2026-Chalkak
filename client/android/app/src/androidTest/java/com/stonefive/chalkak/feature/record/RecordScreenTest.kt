package com.stonefive.chalkak.feature.record

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.feature.record.component.RecordPhotoStackStyle
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordScreenRendersStackAndCalendar() {
        val photo = recordPhoto(day = 2)

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(listOf(photo), selectedDay = 2),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("2026년 8월").assertIsDisplayed()
        composeRule.onNodeWithText("일").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("8월 2일 사진").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2026년 8월 2일 사진").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("월간 책자로 보기").assertIsDisplayed()
        composeRule.onAllNodesWithText("피드에서 보기").assertCountEquals(0)
        composeRule.onAllNodesWithText("전시 보러가기").assertCountEquals(0)
    }

    @Test
    fun focusedPillarOpensFeed() {
        val photo = recordPhoto(day = 2)
        var openedPostId: String? = null
        var openedThumbnail: String? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(listOf(photo), selectedDay = 2),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = { postId, thumbnail ->
                        openedPostId = postId
                        openedThumbnail = thumbnail
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("8월 2일 사진").performClick()

        assertEquals(photo.postId, openedPostId)
        assertEquals(photo.thumbnailImageUrl, openedThumbnail)
    }

    @Test
    fun calendarPhotoBringsPillarToFrontWithoutOpeningFeed() {
        val photos = listOf(recordPhoto(day = 2), recordPhoto(day = 20))
        var openedPostId: String? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(photos, selectedDay = 20),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = { postId, _ -> openedPostId = postId },
                )
            }
        }

        composeRule.onNodeWithContentDescription("2026년 8월 2일 사진").performClick()
        composeRule.waitForIdle()
        assertEquals(null, openedPostId)

        composeRule.onNodeWithContentDescription("8월 2일 사진").performClick()
        assertEquals("post-2", openedPostId)
    }

    @Test
    fun pendingPhotoDoesNotOpenFeed() {
        val photo = recordPhoto(day = 2).copy(status = PostStatus.PENDING)
        var openedPostId: String? = null
        var pendingClicked = false

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(listOf(photo), selectedDay = 2),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = { postId, _ -> openedPostId = postId },
                    onPendingPhotoClick = { pendingClicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("8월 2일 사진").performClick()

        assertEquals(null, openedPostId)
        assertTrue(pendingClicked)
    }

    @Test
    fun monthButtonOpensYearCalendarAndSelectsMonth() {
        val photo = recordPhoto(day = 2)
        var loadedYear: Int? = null
        var shownMonth: YearMonth? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(listOf(photo), selectedDay = 2),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onLoadYear = { loadedYear = it },
                    onShowMonth = { shownMonth = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("2026년 8월").performClick()
        composeRule.onNodeWithContentDescription("2026년").assertIsDisplayed()
        assertEquals(2026, loadedYear)

        composeRule.onNodeWithContentDescription("8월, 기록 1개").performClick()
        assertEquals(RecordTestMonth, shownMonth)
        composeRule.onNodeWithText("일").assertIsDisplayed()
    }

    @Test
    fun stackStyleButtonTogglesStyle() {
        var toggled = false

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = loadedState(listOf(recordPhoto(day = 2)), selectedDay = 2)
                        .copy(photoStackStyle = RecordPhotoStackStyle.BOOK),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onToggleStackStyle = { toggled = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("기둥과 눈금자로 보기").performClick()

        assertTrue(toggled)
    }

    @Test
    fun recordDestinationIsSelectedInBottomBar() {
        var selectedItem: ChalkakBottomBarItem? = null

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(isLoading = false),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = { selectedItem = it },
                )
            }
        }

        composeRule.onNodeWithText("기록").assertIsDisplayed()
        composeRule.onNodeWithText("홈").performClick()

        assertEquals(ChalkakBottomBarItem.HOME, selectedItem)
    }

    @Test
    fun unauthorizedRecordShowsLoginButtonAndNavigatesToLogin() {
        var loginClicked = false

        composeRule.setContent {
            ChalkakTheme {
                RecordScreen(
                    uiState = RecordUiState(
                        month = RecordTestMonth,
                        isLoading = false,
                        errorMessage = "로그인이 필요해요",
                        isLoginRequired = true,
                    ),
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onNavigateToLogin = { loginClicked = true },
                )
            }
        }

        composeRule
            .onNodeWithText("로그인 하기")
            .performScrollTo()
            .performClick()

        assertEquals(true, loginClicked)
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
    }
}

private val RecordTestMonth = YearMonth.of(2026, 8)

private fun loadedState(
    posts: List<PostCalendarItem>,
    selectedDay: Int,
): RecordUiState = RecordUiState(
    month = RecordTestMonth,
    latestMonth = RecordTestMonth,
    availableMonths = listOf(RecordTestMonth),
    isLoading = false,
    posts = posts,
    selectedDate = RecordTestMonth.atDay(selectedDay),
    yearPosts = mapOf(RecordTestMonth to posts),
)

private fun recordPhoto(day: Int): PostCalendarItem = PostCalendarItem(
    postId = "post-$day",
    topicDate = RecordTestMonth.atDay(day),
    thumbnailImageUrl = "android.resource://${BuildConfig.APPLICATION_ID}/drawable/home_feed_photo",
    status = PostStatus.APPROVED,
)
