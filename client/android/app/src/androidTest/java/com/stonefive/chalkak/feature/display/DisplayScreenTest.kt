package com.stonefive.chalkak.feature.display

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class DisplayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `최신 날짜는 정렬과 사진 목록을 표시한다`() {
        setDisplayContent(latestUiState())

        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onAllNodesWithText("가장 사람들이 좋아했던 사진들이에요").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("사진").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("좋아요 17")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription("알림").assertCountEquals(0)
    }

    @Test
    fun `과거 날짜는 인기 사진 영역을 표시하고 정렬은 숨긴다`() {
        setDisplayContent(archiveUiState())

        composeRule.onNodeWithText("가장 사람들이 좋아했던 사진들이에요").assertIsDisplayed()
        composeRule.onNodeWithText("한낮의 다리").assertIsDisplayed()
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)
    }

    private fun setDisplayContent(uiState: DisplayUiState) {
        composeRule.setContent {
            ChalkakTheme {
                DisplayScreen(
                    uiState = uiState,
                    onPreviousDateClick = {},
                    onNextDateClick = {},
                    onSortSelected = {},
                    onFeaturedPageChanged = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                )
            }
        }
    }
}

private val latestDate = LocalDate.of(2026, 8, 5)
private val photo = Post(
    id = "photo",
    imageUrl = "android.resource://com.stonefive.chalkak/drawable/preview_photo",
    signatureUrl = "android.resource://com.stonefive.chalkak/drawable/preview_signature",
    contentDescription = "사진",
    title = "한낮의 다리",
    likeCount = 17,
)

private fun latestUiState() = DisplayUiState(
    selectedDate = latestDate,
    latestDate = latestDate,
    earliestDate = latestDate.minusDays(4),
    topic = "바다",
    content = DisplayContentState.Latest(
        photos = listOf(photo),
        selectedSort = PostSort.LATEST,
    ),
)

private fun archiveUiState() = DisplayUiState(
    selectedDate = latestDate.minusDays(1),
    latestDate = latestDate,
    earliestDate = latestDate.minusDays(4),
    topic = "다리",
    content = DisplayContentState.Archive(
        photos = listOf(photo),
        featuredPhotos = listOf(photo),
    ),
)
