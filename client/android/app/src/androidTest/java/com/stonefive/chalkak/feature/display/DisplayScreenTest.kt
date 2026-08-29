package com.stonefive.chalkak.feature.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DisplayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun latestDateShowsSortingOptionsAndPhotoList() {
        setDisplayContent(latestUiState())

        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onNodeWithText("같은 주제에서 다른 시선을 느껴보세요").assertIsDisplayed()
        composeRule.onAllNodesWithText("가장 사람들이 좋아했던 사진들이에요").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("사진").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("좋아요 17")
            .assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("알림").assertCountEquals(0)
    }

    @Test
    fun emptyDisplayUsesHomeEmptyPhotoMessage() {
        setDisplayContent(
            latestUiState().copy(
                content = DisplayContentState.Latest(
                    photos = emptyList(),
                    selectedSort = PostSort.LATEST,
                ),
            ),
        )

        composeRule.onNodeWithTag(DISPLAY_EMPTY_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("아직 올라온 사진이 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("첫 번째 사진을 올려보세요").assertIsDisplayed()
    }

    @Test
    fun latestDateHeaderAndFilterHideAndRestoreTogetherOnScroll() {
        setDisplayContent(scrollableLatestUiState())

        composeRule.onNodeWithText("8월 5일").assertIsDisplayed()
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        composeRule
            .onNodeWithText("같은 주제에서 다른 시선을 느껴보세요")
            .assertIsDisplayed()
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onNodeWithText("전시").assertIsDisplayed()
        val photoGrid = composeRule.onNode(hasScrollAction())

        photoGrid.performTouchInput { swipeUp() }
        composeRule.onNodeWithText("8월 5일").assertIsNotDisplayed()
        composeRule.onNodeWithText("바다").assertIsNotDisplayed()
        composeRule
            .onNodeWithText("같은 주제에서 다른 시선을 느껴보세요")
            .assertIsNotDisplayed()
        composeRule.onNodeWithText("최신순").assertIsNotDisplayed()
        composeRule.onNodeWithText("전시").assertIsDisplayed()

        photoGrid.performTouchInput { swipeDown() }
        composeRule.onNodeWithText("8월 5일").assertIsDisplayed()
        composeRule.onNodeWithText("바다").assertIsDisplayed()
        composeRule
            .onNodeWithText("같은 주제에서 다른 시선을 느껴보세요")
            .assertIsDisplayed()
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onNodeWithText("전시").assertIsDisplayed()
    }

    @Test
    fun changingSortRestoresFilterAndMovesPhotoGridToFirstItem() {
        var selectedSort: PostSort? = null
        setDisplayContent(
            uiState = scrollableLatestUiState(),
            onSortSelected = { selectedSort = it },
        )
        val photoGrid = composeRule.onNode(hasScrollAction())

        photoGrid.performTouchInput { swipeUp() }
        composeRule.onNodeWithText("최신순").assertIsNotDisplayed()

        photoGrid.performTouchInput {
            swipe(
                start = center,
                end = center.copy(y = center.y + 120f),
            )
        }
        composeRule.onNodeWithText("최신순").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("사진 0").assertCountEquals(0)

        composeRule.onNodeWithText("인기순").performClick()

        assertEquals(PostSort.POPULAR, selectedSort)
        composeRule.onNodeWithText("인기순").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("사진 0").assertIsDisplayed()
    }

    @Test
    fun tappingDisplayPhotoOpensFeedWithSelectedPhotoAndDisplayDetails() {
        var selected: Triple<Post, String, String>? = null
        setDisplayContent(
            uiState = latestUiState(),
            onOpenFeed = { post, dateLabel, topic ->
                selected = Triple(post, dateLabel, topic)
            },
        )

        composeRule
            .onNodeWithContentDescription("사진")
            .assertHasClickAction()
            .performClick()

        assertEquals(
            Triple(photo, "8월 5일의 주제", "바다"),
            selected,
        )
    }

    @Test
    fun pastDateShowsOnlyPopularPhotosWithoutSortingOptions() {
        setDisplayContent(archiveUiState())

        composeRule.onNodeWithText("가장 사람들이 좋아했던 사진들이에요").assertIsDisplayed()
        composeRule.onNodeWithText("한낮의 다리").assertIsDisplayed()
        composeRule.onAllNodesWithText("최신순").assertCountEquals(0)
        composeRule.onAllNodesWithText("인기순").assertCountEquals(0)
        composeRule.onAllNodesWithText("랜덤순").assertCountEquals(0)
    }

    @Test
    fun archiveHeaderCollapsesOnScrollAndReappearsAtTop() {
        setDisplayContent(scrollableArchiveUiState())

        composeRule.onNodeWithText("8월 4일").assertIsDisplayed()
        composeRule.onNodeWithText("다리").assertIsDisplayed()
        composeRule
            .onNodeWithText("가장 사람들이 좋아했던 사진들이에요")
            .assertIsDisplayed()
        val photoGrid = composeRule.onNode(hasScrollAction())

        photoGrid.performTouchInput { swipeUp() }
        composeRule.onNodeWithText("8월 4일").assertIsNotDisplayed()
        composeRule.onNodeWithText("다리").assertIsNotDisplayed()
        composeRule
            .onNodeWithText("가장 사람들이 좋아했던 사진들이에요")
            .assertIsNotDisplayed()

        photoGrid.performTouchInput { swipeDown() }
        composeRule.onNodeWithText("8월 4일").assertIsDisplayed()
        composeRule.onNodeWithText("다리").assertIsDisplayed()
        composeRule
            .onNodeWithText("가장 사람들이 좋아했던 사진들이에요")
            .assertIsDisplayed()
    }

    @Test
    fun dateNavigationHeaderInvokesPreviousAndNextCallbacks() {
        var previousDateClicked = false
        var nextDateClicked = false
        setDisplayContent(
            uiState = archiveUiState(),
            onPreviousDateClick = { previousDateClicked = true },
            onNextDateClick = { nextDateClicked = true },
        )

        composeRule.onNodeWithContentDescription("이전 날짜").performClick()
        composeRule.onNodeWithContentDescription("다음 날짜").performClick()

        assertEquals(true, previousDateClicked)
        assertEquals(true, nextDateClicked)
    }

    private fun setDisplayContent(
        uiState: DisplayUiState,
        onSortSelected: (PostSort) -> Unit = {},
        onOpenFeed: (Post, String, String) -> Unit = { _, _, _ -> },
        onPreviousDateClick: () -> Unit = {},
        onNextDateClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                var currentUiState by remember { mutableStateOf(uiState) }

                DisplayScreen(
                    uiState = currentUiState,
                    onPreviousDateClick = onPreviousDateClick,
                    onNextDateClick = onNextDateClick,
                    onSortSelected = { sort ->
                        currentUiState = currentUiState.copy(
                            content = (currentUiState.content as? DisplayContentState.Latest)
                                ?.copy(selectedSort = sort)
                                ?: currentUiState.content,
                        )
                        onSortSelected(sort)
                    },
                    onFeaturedPageChanged = {},
                    onEndThresholdChanged = {},
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    onOpenFeed = onOpenFeed,
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

private fun scrollableLatestUiState() = latestUiState().copy(
    content = DisplayContentState.Latest(
        photos = List(12) { index ->
            photo.copy(
                id = "photo-$index",
                contentDescription = "사진 $index",
            )
        },
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

private fun scrollableArchiveUiState() = archiveUiState().copy(
    content = DisplayContentState.Archive(
        photos = List(20) { index ->
            photo.copy(
                id = "archive-photo-$index",
                contentDescription = "전시 사진 $index",
            )
        },
        featuredPhotos = listOf(
            photo.copy(
                id = "featured-photo",
                contentDescription = "추천 사진",
            ),
        ),
    ),
)
