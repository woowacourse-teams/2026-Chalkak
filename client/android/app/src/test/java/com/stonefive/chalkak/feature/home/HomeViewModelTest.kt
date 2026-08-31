package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `초기 로드는 주입된 KST 날짜와 recent 첫 페이지를 사용한다`() = runTest {
        val repository = RecordingPostRepository()
        val viewModel = homeViewModel(repository)

        assertEquals(
            HomeQuery(
                date = TEST_DATE,
                sort = PostSort.LATEST,
                page = 1,
            ),
            repository.homeQueries.single(),
        )
        assertEquals(HomeContentStatus.Content, viewModel.uiState.value.contentStatus)
    }

    @Test
    fun `최초 실패 원인을 구분된 오류 상태로 표현한다`() = runTest {
        val cases = listOf(
            HomeFailure.TopicNotFound to HomeInitialError.TopicNotFound,
            HomeFailure.Unauthorized to HomeInitialError.Unauthorized,
            HomeFailure.Network to HomeInitialError.Network,
            HomeFailure.InvalidResponse to HomeInitialError.InvalidResponse,
            HomeFailure.Http(400) to HomeInitialError.Client,
            HomeFailure.Http(503) to HomeInitialError.Server,
            HomeFailure.Http(302) to HomeInitialError.Generic,
        )

        cases.forEach { (failure, expected) ->
            val repository = RecordingPostRepository(
                homeResults = ArrayDeque(listOf(HomeResult.Failure(failure))),
            )

            assertEquals(
                HomeContentStatus.Error(expected),
                homeViewModel(repository)
                    .uiState.value.contentStatus,
            )
        }
    }

    @Test
    fun `빈 결과는 오류가 아닌 content 상태다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(listOf(HomeResult.Success(homeContent(photos = emptyList())))),
        )

        val state = homeViewModel(repository).uiState.value

        assertEquals(HomeContentStatus.Content, state.contentStatus)
        assertTrue(state.photos.isEmpty())
    }

    @Test
    fun `응답 topicDate를 날짜 라벨과 페이지네이션 세션에 사용한다`() = runTest {
        var currentDate = LocalDate.of(2026, 8, 28)
        val canonicalDate = LocalDate.of(2026, 8, 29)
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(
                        homeContent(
                            topic = "새 주제",
                            topicDate = canonicalDate,
                        ),
                    ),
                    HomeResult.Success(
                        homeContent(
                            topic = "다음 주제",
                            topicDate = LocalDate.of(2026, 8, 30),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = homeViewModel(repository, dateProvider = { currentDate })

        assertEquals(
            LocalDate.of(2026, 8, 28),
            repository.homeQueries
                .single()
                .date,
        )
        assertEquals("새 주제", viewModel.uiState.value.topic)
        assertEquals(canonicalDate, viewModel.uiState.value.topicDate)

        currentDate = LocalDate.of(2026, 8, 30)
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        assertEquals(
            canonicalDate,
            repository.pageQueries
                .single()
                .date,
        )

        viewModel.onAction(HomeUiAction.RefreshRequested)
        assertEquals(
            LocalDate.of(2026, 8, 30),
            repository.homeQueries
                .last()
                .date,
        )
        assertEquals("다음 주제", viewModel.uiState.value.topic)
        assertEquals(LocalDate.of(2026, 8, 30), viewModel.uiState.value.topicDate)
    }

    @Test
    fun `첫 페이지의 빈 성공은 이전 목록을 제거하고 새 주제를 적용한다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent(topic = "이전 주제")),
                    HomeResult.Success(
                        homeContent(
                            topic = "새 주제",
                            topicDate = LocalDate.of(2026, 8, 29),
                            photos = emptyList(),
                            hasNext = false,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RefreshRequested)

        assertEquals("새 주제", viewModel.uiState.value.topic)
        assertEquals(LocalDate.of(2026, 8, 29), viewModel.uiState.value.topicDate)
        assertTrue(
            viewModel.uiState.value.photos
                .isEmpty(),
        )
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertFalse(viewModel.uiState.value.hasNext)
    }

    @Test
    fun `재시도는 첫 페이지를 다시 요청하고 복구한다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Failure(HomeFailure.Network),
                    HomeResult.Success(homeContent()),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RetryClicked)

        assertEquals(2, repository.homeQueries.size)
        assertTrue(
            repository.homeQueries.all {
                it.sort == PostSort.LATEST && it.page == 1 && it.randomSeed == null
            },
        )
        assertEquals(HomeContentStatus.Content, viewModel.uiState.value.contentStatus)
    }

    @Test
    fun `수동 새로고침은 매번 seed 없는 랜덤 첫 페이지 새 세션을 요청한다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(homeContent(randomSeed = "seed-1")),
                    HomeResult.Success(homeContent(randomSeed = "seed-2")),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RefreshRequested)
        viewModel.onAction(HomeUiAction.RefreshRequested)

        assertEquals(
            listOf(PostSort.LATEST, PostSort.RANDOM, PostSort.RANDOM),
            repository.homeQueries.map(HomeQuery::sort),
        )
        assertTrue(
            repository.homeQueries
                .drop(1)
                .all { it.page == 1 && it.randomSeed == null },
        )
        assertEquals(PostSort.RANDOM, viewModel.uiState.value.selectedSort)
        assertEquals(2, viewModel.uiState.value.refreshRevision)
    }

    @Test
    fun `수동 새로고침 중에는 콘텐츠를 유지하고 실패하면 원인 이벤트만 보낸다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent(topic = "기존 주제"))
        val viewModel = homeViewModel(repository)
        val before = viewModel.uiState.value

        viewModel.onAction(HomeUiAction.RefreshRequested)

        assertEquals("기존 주제", viewModel.uiState.value.topic)
        assertEquals(before.photos, viewModel.uiState.value.photos)
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(
            PostSort.RANDOM,
            repository.homeQueries
                .last()
                .sort,
        )
        assertEquals(
            null,
            repository.homeQueries
                .last()
                .randomSeed,
        )

        repository.completeHome(0, HomeResult.Failure(HomeFailure.Network))

        assertEquals(before.copy(isRefreshing = false), viewModel.uiState.value)
        assertEquals(
            HomeUiEvent.ShowRefreshFailure(HomeInitialError.Network),
            viewModel.uiEvent.first(),
        )
    }

    @Test
    fun `연속 새로고침은 매번 새로운 랜덤 세션이라 이전 seed를 재사용하지 않는다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(homeContent(randomSeed = "first-seed")),
                    HomeResult.Success(homeContent(randomSeed = "second-seed")),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RefreshRequested)
        viewModel.onAction(HomeUiAction.RefreshRequested)

        val randomQueries = repository.homeQueries.filter { it.sort == PostSort.RANDOM }
        assertEquals(2, randomQueries.size)
        assertTrue(randomQueries.all { it.randomSeed == null })
    }

    @Test
    fun `끝 임계값 false to true는 다음 페이지를 한 번만 요청한다`() = runTest {
        val repository = RecordingPostRepository()
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertEquals(1, repository.pageQueries.size)
        assertEquals(
            2,
            repository.pageQueries
                .single()
                .page,
        )
    }

    @Test
    fun `페이지 성공은 순서를 유지하고 중복 id를 제외하며 seed를 보존한다`() = runTest {
        val repository = RecordingPostRepository(
            pageResults = ArrayDeque(
                listOf(
                    HomeResult.Success(
                        postPage(
                            photos = listOf(post(PHOTO_ID), post("photo-2")),
                            currentPage = 2,
                            hasNext = false,
                            randomSeed = "seed-1",
                        ),
                    ),
                ),
            ),
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(
                        homeContent(
                            randomSeed = "seed-1",
                            sortPhotos = listOf(post(PHOTO_ID)),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RefreshRequested)
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertEquals(
            listOf(PHOTO_ID, "photo-2"),
            viewModel.uiState.value.photos
                .map(Post::id),
        )
        assertEquals(
            "seed-1",
            repository.pageQueries
                .single()
                .randomSeed,
        )
        assertFalse(viewModel.uiState.value.hasNext)
    }

    @Test
    fun `hasNext false면 추가 요청하지 않는다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(listOf(HomeResult.Success(homeContent(hasNext = false)))),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertTrue(repository.pageQueries.isEmpty())
    }

    @Test
    fun `RANDOM seed가 없으면 크래시 또는 추가 요청 없이 pagination을 종료한다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(homeContent(hasNext = true, randomSeed = null)),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.RefreshRequested)
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertTrue(repository.pageQueries.isEmpty())
        assertFalse(viewModel.uiState.value.hasNext)
        assertFalse(viewModel.uiState.value.isLoadingNext)
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
    }

    @Test
    fun `페이지 실패는 콘텐츠와 cursor seed를 유지하고 재진입 때 같은 페이지를 재시도한다`() = runTest {
        val repository = RecordingPostRepository(
            pageResults = ArrayDeque(
                listOf(
                    HomeResult.Failure(HomeFailure.Network),
                    HomeResult.Success(postPage(currentPage = 2, randomSeed = "seed-1")),
                ),
            ),
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(
                        homeContent(
                            randomSeed = "seed-1",
                            sortPhotos = listOf(post(PHOTO_ID)),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)
        viewModel.onAction(HomeUiAction.RefreshRequested)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertEquals(1, repository.pageQueries.size)
        assertEquals(
            listOf(PHOTO_ID),
            viewModel.uiState.value.photos
                .map(Post::id),
        )
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals("seed-1", viewModel.uiState.value.randomSeed)
        assertFalse(viewModel.uiState.value.isLoadingNext)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(false))
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))

        assertEquals(listOf(2, 2), repository.pageQueries.map(HomeQuery::page))
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
    }

    @Test
    fun `page 2 대기 중 refresh 성공은 새 첫 페이지만 표시한다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent(topic = "이전 주제"))
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        assertTrue(viewModel.uiState.value.isLoadingNext)

        viewModel.onAction(HomeUiAction.RefreshRequested)
        assertFalse(viewModel.uiState.value.isLoadingNext)
        viewModel.onAction(HomeUiAction.EndThresholdChanged(false))
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        assertEquals(1, repository.pageQueries.size)

        repository.completeHome(
            0,
            HomeResult.Success(
                homeContent(
                    topic = "새 주제",
                    photos = listOf(post("new-photo")),
                    hasNext = false,
                ),
            ),
        )
        repository.completePage(0, HomeResult.Success(postPage(photos = listOf(post("stale-photo")))))

        assertEquals("새 주제", viewModel.uiState.value.topic)
        assertEquals(
            listOf("new-photo"),
            viewModel.uiState.value.photos
                .map(Post::id),
        )
        assertFalse(viewModel.uiState.value.isLoadingNext)
    }

    @Test
    fun `page 2 대기 중 refresh 실패는 콘텐츠를 유지하고 page 2 재시도를 허용한다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent(topic = "이전 주제"))
        val viewModel = homeViewModel(repository)
        val before = viewModel.uiState.value

        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        viewModel.onAction(HomeUiAction.RefreshRequested)
        repository.completeHome(0, HomeResult.Failure(HomeFailure.Network))

        assertEquals(before, viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.isLoadingNext)

        viewModel.onAction(HomeUiAction.EndThresholdChanged(false))
        viewModel.onAction(HomeUiAction.EndThresholdChanged(true))
        assertEquals(listOf(2, 2), repository.pageQueries.map(HomeQuery::page))

        repository.completePage(1, HomeResult.Success(postPage(photos = listOf(post("retried-photo")))))
        repository.completePage(0, HomeResult.Success(postPage(photos = listOf(post("stale-photo")))))

        assertEquals(
            listOf(PHOTO_ID, "retried-photo"),
            viewModel.uiState.value.photos
                .map(Post::id),
        )
        assertFalse(viewModel.uiState.value.isLoadingNext)
    }

    @Test
    fun `인증 좋아요는 낙관 적용 후 서버 count state로 조정한다`() = runTest {
        val repository = RecordingPostRepository(
            likeResults = ArrayDeque(listOf(HomeResult.Success(HomeLike(likeCount = 30, isLiked = true)))),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))

        assertEquals(PHOTO_ID to true, repository.likeRequests.single())
        assertEquals(
            30,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertEquals(setOf(PHOTO_ID), viewModel.uiState.value.likedPhotoIds)
    }

    @Test
    fun `좋아요 실패는 해당 게시물만 복원한다`() = runTest {
        val repository = RecordingPostRepository(
            likeResults = ArrayDeque(listOf(HomeResult.Failure(HomeFailure.Network))),
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(
                        homeContent(sortPhotos = listOf(post(PHOTO_ID), post("photo-2", likeCount = 10))),
                    ),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))

        assertEquals(
            listOf(24, 10),
            viewModel.uiState.value.photos
                .map(Post::likeCount),
        )
        assertTrue(
            viewModel.uiState.value.likedPhotoIds
                .isEmpty(),
        )
    }

    @Test
    fun `이전 좋아요 응답과 reload 이전 rollback은 최신 상태를 덮지 않는다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent())
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        repository.completeLike(1, HomeResult.Success(HomeLike(24, false)))
        repository.completeLike(0, HomeResult.Success(HomeLike(25, true)))

        assertEquals(
            24,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.likedPhotoIds
                .isEmpty(),
        )

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        viewModel.onAction(HomeUiAction.RefreshRequested)
        assertEquals(1, repository.homeQueries.size)
        repository.completeLike(2, HomeResult.Failure(HomeFailure.Network))
        repository.completeHome(0, HomeResult.Success(homeContent(likeCount = 40, liked = true)))

        assertEquals(
            40,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertEquals(setOf(PHOTO_ID), viewModel.uiState.value.likedPhotoIds)
    }

    @Test
    fun `좋아요 성공을 기다린 뒤 refresh를 시작하고 대기 중 새 좋아요를 차단한다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent())
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        viewModel.onAction(HomeUiAction.RefreshRequested)
        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.areLikesEnabled)
        assertEquals(1, repository.likeRequests.size)
        assertEquals(1, repository.homeQueries.size)

        repository.completeLike(0, HomeResult.Success(HomeLike(likeCount = 30, isLiked = true)))

        assertEquals(2, repository.homeQueries.size)
        assertEquals(
            30,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertEquals(setOf(PHOTO_ID), viewModel.uiState.value.likedPhotoIds)

        repository.completeHome(0, HomeResult.Success(homeContent(likeCount = 31, liked = true)))
        assertEquals(
            31,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertTrue(viewModel.uiState.value.areLikesEnabled)
    }

    @Test
    fun `좋아요 실패 rollback을 기다린 뒤 refresh를 시작한다`() = runTest {
        val repository = ControlledPostRepository(autoInitial = homeContent())
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))
        viewModel.onAction(HomeUiAction.RefreshRequested)
        assertEquals(
            25,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )

        repository.completeLike(0, HomeResult.Failure(HomeFailure.Network))

        assertEquals(
            24,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.likedPhotoIds
                .isEmpty(),
        )
        assertEquals(2, repository.homeQueries.size)

        repository.completeHome(0, HomeResult.Failure(HomeFailure.Network))
        assertEquals(
            24,
            viewModel.uiState.value.photos
                .single()
                .likeCount,
        )
        assertTrue(
            viewModel.uiState.value.likedPhotoIds
                .isEmpty(),
        )
        assertTrue(viewModel.uiState.value.areLikesEnabled)
    }

    @Test
    fun `repository의 예상하지 못한 예외를 Network 초기 오류로 위장하지 않는다`() {
        val uncaught = mutableListOf<Throwable>()
        val viewModel = homeViewModel(
            repository = object : PostRepository {
                override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

                override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

                override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
                    error("unexpected defect")
                }

                override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")

                override suspend fun updateLike(
                    photoId: String,
                    isLiked: Boolean,
                ): HomeResult<HomeLike> = error("unused")
            },
            launchContext = CoroutineExceptionHandler { _, error -> uncaught += error },
        )

        assertTrue(uncaught.single() is IllegalStateException)
        assertEquals(HomeContentStatus.Loading, viewModel.uiState.value.contentStatus)
    }

    @Test
    fun `게스트 좋아요는 정확한 snackbar 이벤트만 보내고 상태와 저장소를 바꾸지 않는다`() = runTest {
        val repository = RecordingPostRepository()
        val viewModel = homeViewModel(
            repository = repository,
            sessionState = UserSessionState.Guest,
        )
        val before = viewModel.uiState.value

        viewModel.onAction(HomeUiAction.LikeClicked(PHOTO_ID))

        assertEquals(before, viewModel.uiState.value)
        assertTrue(repository.likeRequests.isEmpty())
        assertEquals(HomeUiEvent.ShowGuestLikeMessage, viewModel.uiEvent.first())
    }

    @Test
    fun `오늘 탭 재선택은 랜덤 새로고침이고 다른 하단 탭과 추가 이벤트는 유지한다`() = runTest {
        val repository = RecordingPostRepository(
            homeResults = ArrayDeque(
                listOf(
                    HomeResult.Success(homeContent()),
                    HomeResult.Success(homeContent(randomSeed = "seed-1")),
                ),
            ),
        )
        val viewModel = homeViewModel(repository)

        viewModel.onAction(HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.TODAY))
        assertEquals(
            PostSort.RANDOM,
            repository.homeQueries
                .last()
                .sort,
        )
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })

        viewModel.onAction(HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.DISPLAY))
        assertEquals(
            HomeUiEvent.NavigateToBottomBar(ChalkakBottomBarItem.DISPLAY),
            viewModel.uiEvent.first(),
        )

        viewModel.onAction(HomeUiAction.AddClicked)
        assertEquals(HomeUiEvent.OpenPhotoUpload, viewModel.uiEvent.first())
    }
}

private val TEST_DATE: LocalDate = LocalDate.of(2026, 8, 28)
private const val PHOTO_ID = "photo-1"

private fun homeViewModel(
    repository: PostRepository,
    sessionState: UserSessionState = UserSessionState.Authenticated("user-id"),
    dateProvider: () -> LocalDate = { TEST_DATE },
    launchContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
) = HomeViewModel(
    repository = repository,
    sessionState = MutableStateFlow(sessionState),
    dateProvider = dateProvider,
    launchContext = launchContext,
)

private fun homeContent(
    topic: String = "바다",
    topicDate: LocalDate = TEST_DATE,
    photos: List<Post> = listOf(post(PHOTO_ID)),
    sortPhotos: List<Post>? = null,
    likeCount: Int = 24,
    liked: Boolean = false,
    hasNext: Boolean = true,
    randomSeed: String? = null,
) = PostContent(
    topicDate = topicDate,
    topic = topic,
    photos = sortPhotos ?: photos.map { if (it.id == PHOTO_ID) it.copy(likeCount = likeCount) else it },
    likedPhotoIds = if (liked) setOf(PHOTO_ID) else emptySet(),
    currentPage = 1,
    hasNext = hasNext,
    randomSeed = randomSeed,
)

private fun postPage(
    photos: List<Post> = listOf(post("photo-2")),
    currentPage: Int = 2,
    hasNext: Boolean = true,
    randomSeed: String? = null,
) = PostPage(
    photos = photos,
    likedPhotoIds = emptySet(),
    currentPage = currentPage,
    hasNext = hasNext,
    randomSeed = randomSeed,
)

private fun post(
    id: String,
    likeCount: Int = 24,
) = Post(
    id = id,
    originalImageUrl = "https://example.com/$id.jpg",
    thumbnailImageUrl = "https://example.com/$id-thumbnail.jpg",
    signatureOriginalImageUrl = "https://example.com/$id-signature.png",
    signatureThumbnailImageUrl = "https://example.com/$id-signature-thumbnail.png",
    contentDescription = "작품 이미지: $id",
    title = id,
    likeCount = likeCount,
)

private class RecordingPostRepository(
    val homeResults: ArrayDeque<HomeResult<PostContent>> = ArrayDeque(listOf(HomeResult.Success(homeContent()))),
    val pageResults: ArrayDeque<HomeResult<PostPage>> = ArrayDeque(listOf(HomeResult.Success(postPage()))),
    val likeResults: ArrayDeque<HomeResult<HomeLike>> = ArrayDeque(listOf(HomeResult.Success(HomeLike(25, true)))),
) : PostRepository {
    val homeQueries = mutableListOf<HomeQuery>()
    val pageQueries = mutableListOf<HomeQuery>()
    val likeRequests = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        homeQueries += query
        return homeResults.removeFirst()
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> {
        pageQueries += query
        return pageResults.removeFirst()
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> {
        likeRequests += photoId to isLiked
        return likeResults.removeFirst()
    }
}

private class ControlledPostRepository(autoInitial: PostContent? = null) : PostRepository {
    private val homeResults = mutableListOf<CompletableDeferred<HomeResult<PostContent>>>()
    private val pageResults = mutableListOf<CompletableDeferred<HomeResult<PostPage>>>()
    private val likeResults = mutableListOf<CompletableDeferred<HomeResult<HomeLike>>>()
    private val autoInitialResult = autoInitial
    private var servedAutoInitial = false
    val homeQueries = mutableListOf<HomeQuery>()
    val pageQueries = mutableListOf<HomeQuery>()
    val likeRequests = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        homeQueries += query
        if (autoInitialResult != null && !servedAutoInitial) {
            servedAutoInitial = true
            return HomeResult.Success(autoInitialResult)
        }
        return CompletableDeferred<HomeResult<PostContent>>()
            .also(homeResults::add)
            .await()
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> {
        pageQueries += query
        return CompletableDeferred<HomeResult<PostPage>>()
            .also(pageResults::add)
            .await()
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> {
        likeRequests += photoId to isLiked
        return CompletableDeferred<HomeResult<HomeLike>>()
            .also(likeResults::add)
            .await()
    }

    fun completeHome(
        index: Int,
        result: HomeResult<PostContent>,
    ) {
        homeResults[index].complete(result)
    }

    fun completeLike(
        index: Int,
        result: HomeResult<HomeLike>,
    ) {
        likeResults[index].complete(result)
    }

    fun completePage(
        index: Int,
        result: HomeResult<PostPage>,
    ) {
        pageResults[index].complete(result)
    }
}
