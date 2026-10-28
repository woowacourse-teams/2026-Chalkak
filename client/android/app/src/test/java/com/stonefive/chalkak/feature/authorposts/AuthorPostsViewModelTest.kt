package com.stonefive.chalkak.feature.authorposts

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostTitleUpdate
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    @Test
    fun `좋아요는 즉시 표시하고 서버 응답의 개수와 여부로 확정한다`() = runTest {
        val repository = FakeRepository()
        val pending = CompletableDeferred<HomeResult<HomeLike>>()
        repository.pendingLike = pending
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertEquals(listOf("one" to true), repository.likeRequests)
        assertEquals(
            1,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        pending.complete(HomeResult.Success(HomeLike(7, true)))
        advanceUntilIdle()
        assertEquals(
            7,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `이미 좋아요한 사진은 취소하고 0 아래로 줄이지 않는다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one").copy(isLiked = true)), false))
            pendingLike = CompletableDeferred()
        }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertEquals(listOf("one" to false), repository.likeRequests)
        assertEquals(
            0,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        repository.pendingLike!!.complete(HomeResult.Success(HomeLike(0, false)))
        advanceUntilIdle()
    }

    @Test
    fun `최대 좋아요 개수에서도 낙관적 갱신은 음수로 넘치지 않는다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one").copy(likeCount = Int.MAX_VALUE)), false))
            pendingLike = CompletableDeferred()
        }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertEquals(
            Int.MAX_VALUE,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        repository.pendingLike!!.complete(HomeResult.Success(HomeLike(Int.MAX_VALUE, true)))
        advanceUntilIdle()
    }

    @Test
    fun `같은 사진의 요청 중 연타는 무시하지만 다른 사진은 처리한다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one"), post("two")), false))
            pendingLike = CompletableDeferred()
        }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")
        viewModel.updateLike("one")
        viewModel.updateLike("two")
        viewModel.updateLike("missing")

        assertEquals(listOf("one" to true, "two" to true), repository.likeRequests)
        repository.pendingLike!!.complete(HomeResult.Success(HomeLike(1, true)))
        advanceUntilIdle()
        repository.pendingLike = null
        repository.likeResponse = HomeResult.Success(HomeLike(0, false))
        viewModel.updateLike("one")
        assertEquals("one" to false, repository.likeRequests.last())
    }

    @Test
    fun `좋아요 실패는 이전 상태를 복원하고 메시지를 확인한 뒤 지운다`() = runTest {
        val repository = FakeRepository().apply { likeResponse = HomeResult.Failure(HomeFailure.Network) }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertEquals(
            0,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        val message = viewModel.uiState.value.pendingMessage!!
        viewModel.onMessageShown(message.id + 1)
        assertEquals(message, viewModel.uiState.value.pendingMessage)
        viewModel.onMessageShown(message.id)
        assertEquals(null, viewModel.uiState.value.pendingMessage)
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test
    fun `좋아요 예외도 복원하고 재시도할 수 있다`() = runTest {
        val repository = FakeRepository().apply { likeException = IllegalStateException("network") }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        assertTrue(viewModel.uiState.value.pendingMessage != null)
        repository.likeException = null
        viewModel.updateLike("one")
        assertEquals(2, repository.likeRequests.size)
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `취소된 좋아요는 오류 메시지 없이 복원하고 요청 잠금을 해제한다`() = runTest {
        val repository = FakeRepository().apply { likeException = CancellationException("cancelled") }
        val viewModel = AuthorPostsViewModel(repository, "author")

        viewModel.updateLike("one")

        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        assertEquals(null, viewModel.uiState.value.pendingMessage)
        repository.likeException = null
        viewModel.updateLike("one")
        assertEquals(2, repository.likeRequests.size)
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `좋아요 인증 오류는 복원하고 로그인 필요 상태로 남긴다`() = runTest {
        listOf(HomeFailure.Unauthorized, HomeFailure.Http(401)).forEach { failure ->
            val repository = FakeRepository().apply { likeResponse = HomeResult.Failure(failure) }
            val viewModel = AuthorPostsViewModel(repository, "author")

            viewModel.updateLike("one")

            assertFalse(
                viewModel.uiState.value.posts
                    .single()
                    .isLiked,
            )
            assertTrue(viewModel.uiState.value.isLoginRequired)
            viewModel.updateLike("one")
            assertEquals(1, repository.likeRequests.size)
        }
    }

    @Test
    fun `상세의 좋아요 변경은 추가 조회 없이 목록에 반영한다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")

        repository.likeUpdates.emit(PostLikeUpdate("one", 12, true))

        assertEquals(
            12,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
        assertEquals(1, repository.requests.size)
        assertTrue(repository.likeRequests.isEmpty())
    }

    @Test
    fun `목록보다 먼저 받은 상세 좋아요 변경도 첫 페이지 응답이 덮지 않는다`() = runTest {
        val repository = FakeRepository().apply { pending = CompletableDeferred() }
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.likeUpdates.emit(PostLikeUpdate("one", 9, true))

        repository.pending!!.complete(HomeResult.Success(page(1, listOf(post("one")), false)))
        advanceUntilIdle()

        assertEquals(
            9,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `이어보기 응답도 요청 중 변경한 좋아요를 덮지 않는다`() = runTest {
        val repository = FakeRepository()
        val viewModel = AuthorPostsViewModel(repository, "author")
        repository.pending = CompletableDeferred()
        viewModel.onEndThresholdChanged(true)
        repository.likeUpdates.emit(PostLikeUpdate("two", 5, true))
        viewModel.updateLike("one")

        repository.pending!!.complete(HomeResult.Success(page(2, listOf(post("one"), post("two")), false)))
        advanceUntilIdle()

        val posts = viewModel.uiState.value.posts
        assertEquals(listOf("one", "two"), posts.map { it.id })
        assertEquals(listOf(1, 5), posts.map { it.likeCount })
        assertTrue(posts.all { it.isLiked })
    }

    @Test
    fun `실패한 로컬 요청은 그 사이 받은 상세 좋아요 변경을 되돌리지 않는다`() = runTest {
        val repository = FakeRepository().apply { pendingLike = CompletableDeferred() }
        val viewModel = AuthorPostsViewModel(repository, "author")
        viewModel.updateLike("one")
        repository.likeUpdates.emit(PostLikeUpdate("one", 14, false))

        repository.pendingLike!!.complete(HomeResult.Failure(HomeFailure.Network))
        advanceUntilIdle()

        assertEquals(
            14,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `늦게 끝난 로컬 성공은 더 최신 상세 좋아요 변경을 덮지 않는다`() = runTest {
        val repository = FakeRepository().apply { pendingLike = CompletableDeferred() }
        val viewModel = AuthorPostsViewModel(repository, "author")
        viewModel.updateLike("one")
        repository.likeUpdates.emit(PostLikeUpdate("one", 14, false))

        repository.pendingLike!!.complete(HomeResult.Success(HomeLike(1, true)))
        advanceUntilIdle()

        assertEquals(
            14,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
        assertFalse(
            viewModel.uiState.value.posts
                .single()
                .isLiked,
        )
    }

    @Test
    fun `좋아요 응답과 상세 변경은 삭제된 사진을 다시 넣지 않는다`() = runTest {
        val repository = FakeRepository().apply { pendingLike = CompletableDeferred() }
        val viewModel = AuthorPostsViewModel(repository, "author")
        viewModel.updateLike("one")
        viewModel.onPostDeleted("one")

        repository.likeUpdates.emit(PostLikeUpdate("one", 4, true))
        repository.pendingLike!!.complete(HomeResult.Success(HomeLike(5, true)))
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.posts
                .isEmpty(),
        )
        assertEquals(1, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `상세에서 돌아오면 연 사진의 최신 좋아요를 목록에 반영한다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one").copy(likeCount = 1)), false))
            detailResponse = detail("one", likeCount = 2, isLiked = true)
        }
        val viewModel = AuthorPostsViewModel(repository, "author")
        advanceUntilIdle()

        viewModel.onPostOpened("one")
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(listOf("one"), repository.detailRequests)
        val post = viewModel.uiState.value.posts
            .single()
        assertEquals(2, post.likeCount)
        assertTrue(post.isLiked)
    }

    @Test
    fun `상세를 열지 않은 복귀와 두 번째 복귀는 다시 조회하지 않는다`() = runTest {
        val repository = FakeRepository().apply { detailResponse = detail("one", likeCount = 2, isLiked = true) }
        val viewModel = AuthorPostsViewModel(repository, "author")
        advanceUntilIdle()

        viewModel.onResume()
        viewModel.onPostOpened("one")
        viewModel.onResume()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(listOf("one"), repository.detailRequests)
    }

    @Test
    fun `다시 받는 사이 누른 좋아요는 늦게 온 상세 결과로 덮지 않는다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one").copy(likeCount = 1)), false))
            pendingDetail = CompletableDeferred()
            likeResponse = HomeResult.Success(HomeLike(5, true))
        }
        val viewModel = AuthorPostsViewModel(repository, "author")
        advanceUntilIdle()
        viewModel.onPostOpened("one")
        viewModel.onResume()

        viewModel.updateLike("one")
        advanceUntilIdle()
        repository.pendingDetail!!.complete(detail("one", likeCount = 2, isLiked = false))
        advanceUntilIdle()

        val post = viewModel.uiState.value.posts
            .single()
        assertEquals(5, post.likeCount)
        assertTrue(post.isLiked)
    }

    @Test
    fun `상세 조회가 실패하면 목록을 그대로 둔다`() = runTest {
        val repository = FakeRepository().apply {
            response = HomeResult.Success(page(1, listOf(post("one").copy(likeCount = 1)), false))
            detailResponse = HomeResult.Failure(HomeFailure.Http(404))
        }
        val viewModel = AuthorPostsViewModel(repository, "author")
        advanceUntilIdle()

        viewModel.onPostOpened("one")
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(
            1,
            viewModel.uiState.value.posts
                .single()
                .likeCount,
        )
    }

    private class FakeRepository : PostRepository {
        override val likeUpdates = MutableSharedFlow<PostLikeUpdate>(extraBufferCapacity = 16)
        val likeRequests = mutableListOf<Pair<String, Boolean>>()
        var likeResponse: HomeResult<HomeLike> = HomeResult.Success(HomeLike(1, true))
        var pendingLike: CompletableDeferred<HomeResult<HomeLike>>? = null
        var likeException: Exception? = null
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

        val detailRequests = mutableListOf<String>()
        var detailResponse: HomeResult<PostDetail> = HomeResult.Failure(HomeFailure.Network)
        var pendingDetail: CompletableDeferred<HomeResult<PostDetail>>? = null

        override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> {
            detailRequests += postId
            return pendingDetail?.await() ?: detailResponse
        }

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
        ): HomeResult<HomeLike> {
            likeRequests += photoId to isLiked
            likeException?.let { throw it }
            return pendingLike?.await() ?: likeResponse
        }
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

private fun detail(
    id: String,
    likeCount: Int,
    isLiked: Boolean,
): HomeResult<PostDetail> = HomeResult.Success(
    PostDetail(
        post = Post(
            id = id,
            originalImageUrl = "https://example.com/$id.webp",
            thumbnailImageUrl = "https://example.com/$id-thumbnail.webp",
            signatureOriginalImageUrl = "https://example.com/signature.png",
            contentDescription = id,
            title = null,
            likeCount = likeCount,
            isLiked = isLiked,
            authorId = "author",
        ),
        topic = "주제",
        topicDate = LocalDate.of(2026, 10, 26),
    ),
)
