package com.stonefive.chalkak.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `홈에서는 정렬 옵션을 표시하지 않는다`() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)
        composeRule.onAllNodesWithText("인기순").assertCountEquals(0)
        composeRule.onAllNodesWithText("랜덤순").assertCountEquals(0)
    }

    @Test
    fun `홈 사진을 스크롤하면 주제가 사라지고 맨 위에서 다시 표시된다`() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("오늘").assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput { swipeUp() }

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)
        composeRule.onNodeWithText("오늘").assertIsNotDisplayed()

        composeRule
            .onNodeWithContentDescription("사진 1")
            .performTouchInput { swipeDown() }

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("오늘").assertIsDisplayed()
    }

    private fun setHomeContent(uiState: HomeUiState) {
        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = uiState,
                    onAction = {},
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
)
