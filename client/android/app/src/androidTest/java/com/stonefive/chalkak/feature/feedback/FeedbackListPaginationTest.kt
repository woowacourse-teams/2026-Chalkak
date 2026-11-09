package com.stonefive.chalkak.feature.feedback

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedbackListPaginationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullyDuplicatePageResumesPaginationWithoutScrolling() {
        val state = mutableStateOf(
            FeedbackListUiState(
                status = FeedbackListStatus.Content,
                items = listOf(FeedbackListItemUiState("A", "문의", "09:30")),
                currentPage = 1,
                hasNext = true,
            ),
        )
        var requests = 0
        composeRule.setContent {
            ChalkakTheme {
                FeedbackListScreen(
                    uiState = state.value,
                    onBackClick = {},
                    onComposeClick = {},
                    onInquiryClick = {},
                    onRefresh = {},
                    onLoadNextPage = {
                        requests++
                        state.value = state.value.copy(isLoadingMore = true)
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, requests)
            // Page 2 contains only A: the item count and scroll position stay unchanged.
            state.value = state.value.copy(currentPage = 2, isLoadingMore = false)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, requests)
            state.value = state.value.copy(isLoadingMore = false, loadMoreFailed = true)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, requests) }
    }
}
