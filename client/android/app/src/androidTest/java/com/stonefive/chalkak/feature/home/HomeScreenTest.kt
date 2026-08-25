package com.stonefive.chalkak.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun `홈 화면에서 위로 스크롤하면 상단 영역이 사라지고 아래로 스크롤하면 다시 표시된다`() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onNodeWithText("오늘").assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput { swipeUp() }
        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)
        composeRule.onAllNodesWithText("바다").assertCountEquals(0)
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)
        composeRule.onNodeWithText("오늘").assertIsNotDisplayed()

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput { swipeDown() }
        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onNodeWithText("오늘").assertIsDisplayed()
    }

    @Test
    fun `정렬을 변경하면 필터가 다시 표시되고 사진 목록이 첫 항목으로 이동한다`() {
        var selectedSort: PostSort? = null
        setHomeContent(
            uiState = scrollableHomeUiState(),
            onSortSelected = { selectedSort = it },
        )

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput { swipeUp() }
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput {
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
