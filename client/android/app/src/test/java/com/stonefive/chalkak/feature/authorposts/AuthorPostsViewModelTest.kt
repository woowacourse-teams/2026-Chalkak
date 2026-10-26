package com.stonefive.chalkak.feature.authorposts

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorPostsViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `첫 페이지와 다음 페이지는 작성자와 같은 크기로 조회하고 중복을 제거한다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.response = HomeResult.Success(page(2, listOf(post("one"), post("two")), false))

        viewModel.onEndThresholdChanged(true)

        assertEquals(listOf(Triple("author", 1, 20), Triple("author", 2, 20)), repository.requests)
        assertEquals(
            listOf("one", "two"),
            viewModel.uiState.value.posts
                .map { it.id },
        )
        assertFalse(viewModel.uiState.value.hasNext)
        viewModel.onEndThresholdChanged(true)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun `중복 사진만 받은 페이지는 끝 도달 상태이면 다음 페이지를 계속 조회한다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.responses[2] = HomeResult.Success(page(2, listOf(post("one")), true))
        repository.responses[3] = HomeResult.Success(page(3, listOf(post("two")), false))

        viewModel.onEndThresholdChanged(true)

        assertEquals(listOf(1, 2, 3), repository.requests.map { it.second })
        assertEquals(
            listOf("one", "two"),
            viewModel.uiState.value.posts
                .map { it.id },
        )
        assertFalse(viewModel.uiState.value.hasNext)
    }

    @Test
    fun `빈 첫 페이지는 오류나 작가 없음이 아니다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, emptyList(), false))
        }
        val state = AuthorPostsViewModel(repository, "author").uiState.value

        assertTrue(state.posts.isEmpty())
        assertFalse(state.hasError)
        assertFalse(state.isAuthorUnavailable)
        assertFalse(state.isLoading)
    }

    @Test
    fun `이어보기 실패는 기존 목록을 보존하고 명시적 재시도로 같은 페이지를 받는다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.response = HomeResult.Failure(HomeFailure.Network)
        viewModel.onEndThresholdChanged(true)
        assertEquals(
            listOf("one"),
            viewModel.uiState.value.posts
                .map { it.id },
        )
        assertTrue(viewModel.uiState.value.hasError)
        viewModel.onEndThresholdChanged(true)
        assertEquals(2, repository.requests.size)
        repository.response = HomeResult.Success(page(2, listOf(post("two")), false))

        viewModel.retry()

        assertEquals(listOf(1, 2, 2), repository.requests.map { it.second })
        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(
            listOf("one", "two"),
            viewModel.uiState.value.posts
                .map { it.id },
        )
    }

    @Test
    fun `첫 요청 실패 후 재시도는 첫 페이지를 요청한다`() = runTest {
        val repository = FakeRepository().apply { response = HomeResult.Failure(HomeFailure.Network) }
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.response = HomeResult.Success(page(1, listOf(post("one")), false))

        viewModel.retry()

        assertEquals(listOf(1, 1), repository.requests.map { it.second })
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test
    fun `404는 작가 없음으로 처리하고 이어보기에서 받은 경우에도 기존 사진을 지운다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.response = HomeResult.Failure(HomeFailure.Http(404))

        viewModel.onEndThresholdChanged(true)

        assertTrue(viewModel.uiState.value.isAuthorUnavailable)
        assertTrue(
            viewModel.uiState.value.posts
                .isEmpty(),
        )
        assertFalse(viewModel.uiState.value.hasError)
        viewModel.retry()
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun `401은 로그인 필요 상태로 남긴다`() = runTest {
        val repository = FakeRepository().apply { response = HomeResult.Failure(HomeFailure.Unauthorized) }

        val state = AuthorPostsViewModel(repository, "author").uiState.value

        assertTrue(state.isLoginRequired)
        assertFalse(state.hasError)
    }

    @Test
    fun `요청 중 반복된 끝 도달은 중복 요청하지 않는다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        val pending = CompletableDeferred<HomeResult<AuthorPostsPage>>()
        repository.pending = pending
        viewModel.onEndThresholdChanged(true)
        viewModel.onEndThresholdChanged(true)
        assertEquals(2, repository.requests.size)
        assertTrue(viewModel.uiState.value.isLoadingNext)

        pending.complete(HomeResult.Success(page(2, emptyList(), false)))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingNext)
        assertFalse(viewModel.uiState.value.hasNext)
    }

    @Test
    fun `삭제한 사진만 제거하고 페이지를 유지한다`() = runTest {
        val viewModel = AuthorPostsViewModel(FakeRepository(), "author")

        viewModel.onPostDeleted("one")

        assertTrue(
            viewModel.uiState.value.posts
                .isEmpty(),
        )
        assertEquals(1, viewModel.uiState.value.currentPage)
    }

    private class FakeRepository : PostRepository {
        val requests = mutableListOf<Triple<String, Int, Int>>()
        var response: HomeResult<AuthorPostsPage> = HomeResult.Success(page(1, listOf(post("one")), true))
        var pending: CompletableDeferred<HomeResult<AuthorPostsPage>>? = null
        val responses = mutableMapOf<Int, HomeResult<AuthorPostsPage>>()

        override suspend fun getAuthorPosts(
            authorId: String,
            page: Int,
            pageSize: Int,
        ): HomeResult<AuthorPostsPage> {
            requests += Triple(authorId, page, pageSize)
            return pending?.await() ?: responses[page] ?: response
        }

        override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("Unused")

        override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("Unused")

        override suspend fun deletePost(postId: String): HomeResult<Unit> = error("Unused")

        override suspend fun updatePostTitle(
            postId: String,
            title: String?,
        ): HomeResult<PostTitleUpdate> = error("Unused")

        override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> = error("Unused")

        override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("Unused")

        override suspend fun updateLike(
            photoId: String,
            isLiked: Boolean,
        ): HomeResult<HomeLike> = error("Unused")
    }
}

private fun page(
    number: Int,
    posts: List<AuthorPost>,
    hasNext: Boolean,
) = AuthorPostsPage(number, 20, hasNext, posts)

private fun post(id: String) = AuthorPost(
    id = id,
    originalImageUrl = "https://example.com/$id.webp",
    thumbnailImageUrl = null,
    topic = Topic("topic", "주제", LocalDate.of(2026, 10, 26)),
)
