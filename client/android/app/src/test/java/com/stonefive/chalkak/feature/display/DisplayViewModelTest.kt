package com.stonefive.chalkak.feature.display

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
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
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DisplayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakePostRepository
    private lateinit var viewModel: DisplayViewModel

    @Before
    fun setUp() {
        repository = FakePostRepository()
        viewModel = displayViewModel(repository)
    }

    @Test
    fun `화면 진입 시 최신 전시 상태를 만든다`() = runTest {
        val content = viewModel.uiState.value.content

        assertEquals(LATEST_DATE, viewModel.uiState.value.selectedDate)
        assertTrue(content is DisplayContentState.Latest)
        assertEquals(PostSort.LATEST, (content as DisplayContentState.Latest).selectedSort)
        assertEquals(listOf(firstPageQuery(LATEST_DATE, PostSort.LATEST)), repository.requests)
    }

    @Test
    fun `전달받은 날짜의 전시 상태로 시작한다`() = runTest {
        val selectedRepository = FakePostRepository()
        val selectedViewModel = displayViewModel(
            repository = selectedRepository,
            initialDate = ARCHIVE_DATE,
        )

        assertEquals(ARCHIVE_DATE, selectedViewModel.uiState.value.selectedDate)
        assertEquals(
            listOf(firstPageQuery(ARCHIVE_DATE, PostSort.POPULAR)),
            selectedRepository.requests,
        )
    }

    @Test
    fun `초기 전시 실패도 요청 날짜를 유지하고 지속 오류로 표시한다`() = runTest {
        val selectedRepository = FakePostRepository().apply {
            firstPageFailure = HomeFailure.Network
        }

        val selectedViewModel = displayViewModel(
            repository = selectedRepository,
            initialDate = ARCHIVE_DATE,
        )

        assertEquals(ARCHIVE_DATE, selectedViewModel.uiState.value.selectedDate)
        assertEquals(
            DisplayContentState.Error("전시를 불러오지 못했어요"),
            selectedViewModel.uiState.value.content,
        )
    }

    @Test
    fun `이전 날짜로 이동하면 과거 전시 상태를 만든다`() = runTest {
        viewModel.moveToPreviousDate()

        val content = viewModel.uiState.value.content
        assertEquals(ARCHIVE_DATE, viewModel.uiState.value.selectedDate)
        assertTrue(content is DisplayContentState.Archive)
        assertEquals(2, (content as DisplayContentState.Archive).featuredPhotos.size)
        assertEquals(firstPageQuery(ARCHIVE_DATE, PostSort.POPULAR), repository.requests.last())
    }

    @Test
    fun `과거 날짜에서는 정렬 변경을 무시한다`() = runTest {
        viewModel.moveToPreviousDate()
        val requestCount = repository.requests.size

        viewModel.selectSort(PostSort.POPULAR)

        assertTrue(viewModel.uiState.value.content is DisplayContentState.Archive)
        assertEquals(requestCount, repository.requests.size)
    }

    @Test
    fun `최신 날짜에서는 선택한 정렬로 다시 불러온다`() = runTest {
        viewModel.selectSort(PostSort.POPULAR)

        val content = viewModel.uiState.value.content as DisplayContentState.Latest
        assertEquals(PostSort.POPULAR, content.selectedSort)
        assertEquals(firstPageQuery(LATEST_DATE, PostSort.POPULAR), repository.requests.last())
    }

    @Test
    fun `정렬 갱신 실패는 기존 전시를 유지하고 Toast 메시지를 보낸다`() = runTest {
        val previousContent = viewModel.uiState.value.content
        repository.firstPageFailure = HomeFailure.Network

        viewModel.selectSort(PostSort.POPULAR)

        assertEquals(previousContent, viewModel.uiState.value.content)
        assertEquals(
            "전시를 불러오지 못했어요",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `과거 날짜는 인기순으로 불러오고 최신 날짜로 돌아오면 기존 정렬을 복원한다`() = runTest {
        viewModel.selectSort(PostSort.RANDOM)

        viewModel.moveToPreviousDate()

        assertEquals(firstPageQuery(ARCHIVE_DATE, PostSort.POPULAR), repository.requests.last())

        viewModel.moveToNextDate()

        val content = viewModel.uiState.value.content as DisplayContentState.Latest
        assertEquals(PostSort.RANDOM, content.selectedSort)
        assertEquals(firstPageQuery(LATEST_DATE, PostSort.RANDOM), repository.requests.last())
    }

    @Test
    fun `과거 전시 페이지를 변경한다`() = runTest {
        viewModel.moveToPreviousDate()

        viewModel.updateFeaturedPage(1)

        val content = viewModel.uiState.value.content as DisplayContentState.Archive
        assertEquals(1, content.featuredPage)
    }

    @Test
    fun `최신 전시에서는 과거 전시 페이지 변경을 무시한다`() = runTest {
        val content = viewModel.uiState.value.content

        viewModel.updateFeaturedPage(1)

        assertEquals(content, viewModel.uiState.value.content)
    }

    @Test
    fun `랜덤 정렬의 다음 페이지는 첫 응답의 시드를 재사용한다`() = runTest {
        val randomRepository = FakePostRepository().apply {
            firstPageHasNext = true
            firstPageRandomSeed = "seed-1"
            nextPageResult = HomeResult.Success(
                PostPage(
                    photos = listOf(post.copy(id = "next-photo")),
                    likedPhotoIds = emptySet(),
                    currentPage = 2,
                    hasNext = false,
                    randomSeed = "seed-1",
                ),
            )
        }
        val randomViewModel = displayViewModel(randomRepository)

        randomViewModel.selectSort(PostSort.RANDOM)
        randomViewModel.updateEndThreshold(true)

        assertEquals(
            HomeQuery(
                date = LATEST_DATE,
                sort = PostSort.RANDOM,
                page = 2,
                randomSeed = "seed-1",
            ),
            randomRepository.pageRequests.single(),
        )
        assertEquals(
            2,
            (randomViewModel.uiState.value.content as DisplayContentState.Latest).photos.size,
        )
    }

    @Test
    fun `과거 전시의 다음 페이지도 인기순으로 요청한다`() = runTest {
        val archiveRepository = FakePostRepository().apply {
            firstPageHasNext = true
            nextPageResult = HomeResult.Success(
                PostPage(
                    photos = listOf(post.copy(id = "archive-next")),
                    likedPhotoIds = emptySet(),
                    currentPage = 2,
                    hasNext = false,
                    randomSeed = null,
                ),
            )
        }
        val archiveViewModel = displayViewModel(archiveRepository)

        archiveViewModel.moveToPreviousDate()
        archiveViewModel.updateEndThreshold(true)

        val pageQuery = archiveRepository.pageRequests.single()
        assertEquals(PostSort.POPULAR, pageQuery.sort)
        assertEquals(null, pageQuery.randomSeed)
    }

    @Test
    fun `다음 페이지 요청이 즉시 완료되어도 다시 요청할 수 있다`() = runTest {
        val pagingRepository = FakePostRepository().apply {
            firstPageHasNext = true
            nextPageResult = HomeResult.Success(
                PostPage(
                    photos = listOf(post.copy(id = "next-photo")),
                    likedPhotoIds = emptySet(),
                    currentPage = 2,
                    hasNext = true,
                    randomSeed = null,
                ),
            )
        }
        val pagingViewModel = displayViewModel(pagingRepository)

        pagingViewModel.updateEndThreshold(true)
        pagingViewModel.updateEndThreshold(false)
        pagingViewModel.updateEndThreshold(true)

        assertEquals(2, pagingRepository.pageRequests.size)
    }

    @Test
    fun `이전 날짜에 주제가 없으면 현재 날짜를 최초 전시일로 확정한다`() = runTest {
        repository.topicNotFoundDates = setOf(ARCHIVE_DATE)

        viewModel.moveToPreviousDate()

        val state = viewModel.uiState.value
        assertEquals(LATEST_DATE, state.selectedDate)
        assertEquals(LATEST_DATE, state.earliestDate)
        assertFalse(state.canGoPrevious)
    }

    @Test
    fun `이전 날짜 요청이 네트워크 오류로 실패하면 최초 전시일을 확정하지 않는다`() = runTest {
        val previousContent = viewModel.uiState.value.content
        repository.firstPageFailure = HomeFailure.Network

        viewModel.moveToPreviousDate()

        val state = viewModel.uiState.value
        assertEquals(LATEST_DATE, state.selectedDate)
        assertEquals(previousContent, state.content)
        assertEquals(null, state.earliestDate)
        assertTrue(state.canGoPrevious)
        assertEquals(
            "전시를 불러오지 못했어요",
            (state.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `다음 날짜에 주제가 없어도 기존 최초 전시일은 보존한다`() = runTest {
        val archiveRepository = FakePostRepository().apply {
            topicNotFoundDates = setOf(EARLIEST_DATE.minusDays(1))
        }
        val archiveViewModel = displayViewModel(
            repository = archiveRepository,
            initialDate = EARLIEST_DATE,
        )

        archiveViewModel.moveToPreviousDate()
        archiveRepository.topicNotFoundDates = emptySet()
        archiveViewModel.moveToNextDate()
        val selectedDate = requireNotNull(archiveViewModel.uiState.value.selectedDate)
        archiveRepository.topicNotFoundDates = setOf(selectedDate.plusDays(1))
        archiveViewModel.moveToNextDate()

        val state = archiveViewModel.uiState.value
        assertEquals(selectedDate, state.selectedDate)
        assertEquals(EARLIEST_DATE, state.earliestDate)
        assertTrue(state.canGoPrevious)
    }
}

private val LATEST_DATE: LocalDate = LocalDate.of(2026, 8, 5)
private val ARCHIVE_DATE: LocalDate = LocalDate.of(2026, 8, 4)
private val EARLIEST_DATE: LocalDate = LocalDate.of(2026, 8, 1)

private class FakePostRepository : PostRepository {
    val requests = mutableListOf<HomeQuery>()
    val pageRequests = mutableListOf<HomeQuery>()
    var firstPageHasNext = false
    var firstPageRandomSeed: String? = null
    var topicNotFoundDates: Set<LocalDate> = emptySet()
    var firstPageFailure: HomeFailure? = null
    var nextPageResult: HomeResult<PostPage> = HomeResult.Success(
        PostPage(
            photos = emptyList(),
            likedPhotoIds = emptySet(),
            currentPage = 2,
            hasNext = false,
            randomSeed = null,
        ),
    )

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> = error("unused")

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> = error("unused")

    override suspend fun deletePost(postId: String): HomeResult<Unit> = error("unused")

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        requests += query
        firstPageFailure?.let { return HomeResult.Failure(it) }
        val selectedDate = query.date
        if (selectedDate in topicNotFoundDates) {
            return HomeResult.Failure(HomeFailure.TopicNotFound)
        }
        return HomeResult.Success(
            PostContent(
                topicDate = selectedDate,
                topic = if (selectedDate == LATEST_DATE) "바다" else "다리",
                photos = if (selectedDate < LATEST_DATE) {
                    listOf(post, post.copy(id = "archive-photo"))
                } else {
                    listOf(post)
                },
                likedPhotoIds = emptySet(),
                hasNext = firstPageHasNext,
                randomSeed = firstPageRandomSeed,
            ),
        )
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> {
        pageRequests += query
        return nextPageResult
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = error("unused")
}

private fun displayViewModel(
    repository: PostRepository,
    initialDate: LocalDate? = null,
) = DisplayViewModel(
    repository = repository,
    initialDate = initialDate,
    dateProvider = { LATEST_DATE },
)

private fun firstPageQuery(
    date: LocalDate,
    sort: PostSort,
) = HomeQuery(
    date = date,
    sort = sort,
    page = HomeQuery.FIRST_PAGE,
)

private val post = Post(
    id = "photo",
    originalImageUrl = "https://example.com/photo.jpg",
    thumbnailImageUrl = "https://example.com/photo-thumbnail.jpg",
    signatureOriginalImageUrl = "https://example.com/signature.png",
    signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
    contentDescription = "사진",
    title = "한낮의 다리",
    likeCount = 17,
)
