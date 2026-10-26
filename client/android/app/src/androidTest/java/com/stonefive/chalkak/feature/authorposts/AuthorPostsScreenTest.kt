package com.stonefive.chalkak.feature.authorposts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.Topic
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthorPostsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyAuthorShowsDescriptionAndEmptyMessage() {
        show(AuthorPostsUiState(isLoading = false))

        composeRule.onNodeWithText("한 작가님의 시선을 따라가 보세요").assertIsDisplayed()
        composeRule.onNodeWithText("아직 공개된 사진이 없어요").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("작성자 사인").assertExists()
    }

    @Test
    fun myPostsUseOwnDescriptionAndSignatureLabel() {
        show(AuthorPostsUiState(isLoading = false), isMine = true)

        composeRule.onNodeWithText("작가님이 공개한 시선들이에요").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("내 사인").assertExists()
    }

    @Test
    fun unavailableAuthorHidesSignatureAndDescription() {
        show(AuthorPostsUiState(isLoading = false, isAuthorUnavailable = true))

        composeRule.onNodeWithText("더 이상 사진을 볼 수 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("한 작가님의 시선을 따라가 보세요").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("작성자 사인").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
    }

    @Test
    fun errorShowsRetryAction() {
        var retries = 0
        show(AuthorPostsUiState(isLoading = false, hasError = true), onRetry = { retries++ })

        composeRule.onNodeWithText("사진을 불러오지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun descriptionScrollsWithGridAndTopBarRemainsVisible() {
        show(
            AuthorPostsUiState(
                isLoading = false,
                posts = List(30) { index -> photo(index.toString()) },
            ),
        )

        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)

        composeRule.onNodeWithText("한 작가님의 시선을 따라가 보세요").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("작성자 사인").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
    }

    @Test
    fun photoClickPassesImageAndSignatureWithoutShowingUnknownLikes() {
        var selectedPhoto: Post? = null
        show(
            AuthorPostsUiState(isLoading = false, posts = listOf(photo("one"))),
            onPhotoClick = { selectedPhoto = it },
        )

        composeRule.onNodeWithContentDescription("2026-10-26 오늘의 주제").performClick()

        assertEquals("one", selectedPhoto?.id)
        assertEquals("https://example.com/one.webp", selectedPhoto?.thumbnailImageUrl)
        assertEquals("https://example.com/signature.png", selectedPhoto?.signatureOriginalImageUrl)
        composeRule.onNodeWithContentDescription("좋아요 0").assertDoesNotExist()
    }

    private fun show(
        state: AuthorPostsUiState,
        isMine: Boolean = false,
        onRetry: () -> Unit = {},
        onPhotoClick: (Post) -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                AuthorPostsScreen(
                    uiState = state,
                    signatureThumbnailImageUrl = "https://example.com/signature.png",
                    isMine = isMine,
                    onNavigateBack = {},
                    onPhotoClick = onPhotoClick,
                    onRetry = onRetry,
                    onEndThresholdChanged = {},
                )
            }
        }
    }
}

private fun photo(id: String) = AuthorPost(
    id = id,
    originalImageUrl = "https://example.com/$id.webp",
    thumbnailImageUrl = null,
    topic = Topic("topic", "오늘의 주제", LocalDate.of(2026, 10, 26)),
)
