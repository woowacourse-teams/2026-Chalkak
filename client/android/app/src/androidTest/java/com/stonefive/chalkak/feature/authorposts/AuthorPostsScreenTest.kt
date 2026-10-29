package com.stonefive.chalkak.feature.authorposts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.feature.feed.FeedScreen
import com.stonefive.chalkak.feature.feed.FeedUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthorPostsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingIndicatorIsCenteredInTheAvailableBody() {
        show(AuthorPostsUiState(isLoading = true))

        val body = composeRule
            .onNodeWithTag("author-posts-body")
            .fetchSemanticsNode()
            .boundsInRoot
        val loading = composeRule
            .onNodeWithTag("author-posts-loading")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(body.center.x, loading.center.x, 1f)
        assertEquals(body.center.y, loading.center.y, 1f)
    }

    @Test
    fun largeSignatureCollapsesIntoSmallTopBarWhenGridIsScrolled() {
        show(
            AuthorPostsUiState(
                isLoading = false,
                posts = List(30) { index -> photo(index.toString()) },
            ),
        )
        composeRule.onNodeWithTag("author-posts-header").assertHeightIsEqualTo(112.dp)
        composeRule.onNodeWithTag("author-posts-signature").assertHeightIsEqualTo(64.dp)
        val backButtonTop = composeRule
            .onNodeWithContentDescription("뒤로 가기")
            .fetchSemanticsNode()
            .boundsInRoot.top

        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }

        composeRule.onNodeWithTag("author-posts-header").assertHeightIsEqualTo(62.dp)
        composeRule.onNodeWithTag("author-posts-signature").assertHeightIsEqualTo(32.dp)
        composeRule.onNodeWithContentDescription("작성자 사인").assertIsDisplayed()
        assertEquals(
            backButtonTop,
            composeRule
                .onNodeWithContentDescription("뒤로 가기")
                .fetchSemanticsNode()
                .boundsInRoot.top,
            1f,
        )
    }

    @Test
    fun authorBackButtonMatchesFeedBeforeAndAfterHeaderCollapse() {
        var showAuthor by mutableStateOf(false)
        composeRule.setContent {
            ChalkakTheme {
                if (showAuthor) {
                    AuthorPostsScreen(
                        uiState = AuthorPostsUiState(
                            isLoading = false,
                            posts = List(30) { index -> photo(index.toString()) },
                        ),
                        signatureThumbnailImageUrl = null,
                        isMine = false,
                        onNavigateBack = {},
                        onPhotoClick = {},
                        onRetry = {},
                        onEndThresholdChanged = {},
                        authorId = "author",
                    )
                } else {
                    FeedScreen(
                        uiState = FeedUiState(isLoading = true),
                        onNavigateBack = {},
                        onDeleteClick = {},
                        onLikeClick = {},
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
        val feedBackBounds = composeRule
            .onNodeWithContentDescription("뒤로 가기")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle { showAuthor = true }

        assertEquals(
            feedBackBounds,
            composeRule
                .onNodeWithContentDescription("뒤로 가기")
                .fetchSemanticsNode()
                .boundsInRoot,
        )
        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        assertEquals(
            feedBackBounds,
            composeRule
                .onNodeWithContentDescription("뒤로 가기")
                .fetchSemanticsNode()
                .boundsInRoot,
        )
    }

    @Test
    fun emptyAuthorShowsDescriptionAndEmptyMessage() {
        show(AuthorPostsUiState(isLoading = false))

        composeRule.onNodeWithText("이 작가님의 시선을 감상해보세요").assertIsDisplayed()
        composeRule.onNodeWithText("아직 공개된 사진이 없어요").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("작성자 사인").assertExists()
    }

    @Test
    fun descriptionIsStartAligned() {
        show(AuthorPostsUiState(isLoading = false))
        val layouts = mutableListOf<TextLayoutResult>()

        composeRule
            .onNodeWithText("이 작가님의 시선을 감상해보세요")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertEquals(0f, layouts.single().getLineLeft(0), 1f)
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
        composeRule.onNodeWithText("이 작가님의 시선을 감상해보세요").assertDoesNotExist()
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

        repeat(2) { composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }

        composeRule.onNodeWithText("이 작가님의 시선을 감상해보세요").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("작성자 사인").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
    }

    @Test
    fun photoClickPassesImageSignatureAndServerLikes() {
        var selectedPhoto: Post? = null
        show(
            AuthorPostsUiState(isLoading = false, posts = listOf(photo("one").copy(likeCount = 7, isLiked = true))),
            onPhotoClick = { selectedPhoto = it },
        )

        composeRule.onNodeWithContentDescription("2026-10-26 오늘의 주제").performClick()

        assertEquals("one", selectedPhoto?.id)
        assertEquals("https://example.com/one.webp", selectedPhoto?.thumbnailImageUrl)
        assertEquals("https://example.com/signature.png", selectedPhoto?.signatureOriginalImageUrl)
        assertEquals(7, selectedPhoto?.likeCount)
        assertEquals(true, selectedPhoto?.isLiked)
        composeRule.onNodeWithContentDescription("좋아요 7").assertIsDisplayed()
    }

    @Test
    fun likeClickUsesCardCallbackWithoutOpeningPhoto() {
        var selectedLike: Post? = null
        var photoClicks = 0
        show(
            AuthorPostsUiState(isLoading = false, posts = listOf(photo("one").copy(likeCount = 7, isLiked = true))),
            onLikeClick = { selectedLike = it },
            onPhotoClick = { photoClicks++ },
        )

        composeRule
            .onNodeWithContentDescription("좋아요 7")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "좋아요 선택됨"))
            .performClick()

        assertEquals("one", selectedLike?.id)
        assertEquals(true, selectedLike?.isLiked)
        assertEquals(0, photoClicks)
    }

    @Test
    fun zeroLikesAreDisplayedAsUnselected() {
        show(AuthorPostsUiState(isLoading = false, posts = listOf(photo("zero"))))

        composeRule
            .onNodeWithContentDescription("좋아요 0")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "좋아요 선택 안 됨"))
    }

    private fun show(
        state: AuthorPostsUiState,
        isMine: Boolean = false,
        onRetry: () -> Unit = {},
        onPhotoClick: (Post) -> Unit = {},
        onLikeClick: (Post) -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                AuthorPostsScreen(
                    uiState = state,
                    signatureThumbnailImageUrl = "https://example.com/signature.png",
                    isMine = isMine,
                    onNavigateBack = {},
                    onPhotoClick = onPhotoClick,
                    onLikeClick = onLikeClick,
                    onRetry = onRetry,
                    onEndThresholdChanged = {},
                    authorId = "author",
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
