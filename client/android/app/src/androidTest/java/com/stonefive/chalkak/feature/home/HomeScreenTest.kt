package com.stonefive.chalkak.feature.home

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
    fun `주제가 접힌 뒤 로고는 스크롤 방향과 무관하게 고정된다`() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)
        composeRule.onNodeWithText("오늘").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipe(
                start = center,
                end = center.copy(y = center.y + 40f),
            )
        }
        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)

        composeRule.onRoot().performTouchInput {
            swipe(
                start = center,
                end = center.copy(y = center.y - 40f),
            )
        }
        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)
    }

    @Test
    fun `홈 재선택 신호는 로고와 주제를 유지하며 첫 사진으로 돌아간다`() {
        val resetSignal = mutableIntStateOf(0)

        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = scrollableHomeUiState(),
                    onAction = {},
                    resetSignal = resetSignal.intValue,
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onAllNodesWithText("8월 5일 · 오늘의 주제").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("사진 0").assertCountEquals(0)

        composeRule.runOnIdle { resetSignal.intValue++ }

        composeRule.onNodeWithText("Chalkak").assertIsDisplayed()
        composeRule.onNodeWithText("8월 5일 · 오늘의 주제").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("사진 0").assertIsDisplayed()
    }

    @Test
    fun `스크롤 최상단 버튼을 누르면 첫 사진으로 돌아간다`() {
        setHomeContent(scrollableHomeUiState())

        composeRule.onAllNodesWithContentDescription("맨 위로").assertCountEquals(0)

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("맨 위로").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("맨 위로").performClick()

        composeRule.onNodeWithContentDescription("사진 0").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("맨 위로").assertCountEquals(0)
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
