package com.stonefive.chalkak.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sortingFilterHidesOnDownwardScrollAndReappearsOnUpwardScroll() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        val photoList = composeRule.onNode(hasScrollAction())

        photoList.performTouchInput { swipeUp() }
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)

        photoList.performTouchInput { swipeDown() }
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
    }

    @Test
    fun changingSortRestoresFilterAndMovesPhotoListToFirstItem() {
        var selectedSort: PostSort? = null
        setHomeContent(
            uiState = scrollableHomeUiState(),
            onSortSelected = { selectedSort = it },
        )

        val photoList = composeRule.onNode(hasScrollAction())
        photoList.performTouchInput { swipeUp() }
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)
        photoList.performScrollToIndex(10)
        composeRule.onAllNodesWithContentDescription("사진 0").assertCountEquals(0)

        photoList.performTouchInput {
            swipe(
                start = center,
                end = center.copy(y = center.y + 40f),
            )
        }
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("사진 0").assertCountEquals(0)

        composeRule.onNodeWithText("인기순").performClick()

        assertEquals(PostSort.POPULAR, selectedSort)
        composeRule.onNodeWithText("인기순").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("사진 0").assertIsDisplayed()
    }

    private fun setHomeContent(
        uiState: HomeUiState,
        onSortSelected: (PostSort) -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                var currentUiState by remember { mutableStateOf(uiState) }

                HomeScreen(
                    uiState = currentUiState,
                    onAction = { action ->
                        if (action is HomeUiAction.SortSelected) {
                            currentUiState = currentUiState.copy(selectedSort = action.sort)
                            onSortSelected(action.sort)
                        }
                    },
                )
            }
        }
    }
}

private fun scrollableHomeUiState() = HomeUiState(
    isLoading = false,
    dateLabel = "8월 5일 · 오늘의 주제",
    topic = "바다",
    photos = List(12) { index ->
        Post(
            id = "photo-$index",
            imageUrl = "android.resource://com.stonefive.chalkak/drawable/preview_photo",
            signatureUrl = "android.resource://com.stonefive.chalkak/drawable/preview_signature",
            contentDescription = "사진 $index",
            title = "사진 $index",
            likeCount = 17,
        )
    },
    selectedSort = PostSort.LATEST,
)
