package com.stonefive.chalkak.feature.display

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.DisplayContent
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.DisplayRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DisplayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeDisplayRepository
    private lateinit var viewModel: DisplayViewModel

    @Before
    fun setUp() {
        repository = FakeDisplayRepository()
        viewModel = DisplayViewModel(repository)
    }

    @Test
    fun `화면 진입 시 최신 전시 상태를 만든다`() = runTest {
        val content = viewModel.uiState.value.content

        assertEquals(LATEST_DATE, viewModel.uiState.value.selectedDate)
        assertTrue(content is DisplayContentState.Latest)
        assertEquals(PostSort.LATEST, (content as DisplayContentState.Latest).selectedSort)
        assertEquals(listOf(null to PostSort.LATEST), repository.requests)
    }

    @Test
    fun `이전 날짜로 이동하면 과거 전시 상태를 만든다`() = runTest {
        viewModel.moveToPreviousDate()

        val content = viewModel.uiState.value.content
        assertEquals(ARCHIVE_DATE, viewModel.uiState.value.selectedDate)
        assertTrue(content is DisplayContentState.Archive)
        assertEquals(2, (content as DisplayContentState.Archive).featuredPhotos.size)
        assertEquals(ARCHIVE_DATE to PostSort.LATEST, repository.requests.last())
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
        assertEquals(LATEST_DATE to PostSort.POPULAR, repository.requests.last())
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
}

private val LATEST_DATE: LocalDate = LocalDate.of(2026, 8, 5)
private val ARCHIVE_DATE: LocalDate = LocalDate.of(2026, 8, 4)
private val EARLIEST_DATE: LocalDate = LocalDate.of(2026, 8, 1)

private class FakeDisplayRepository : DisplayRepository {
    val requests = mutableListOf<Pair<LocalDate?, PostSort>>()

    override suspend fun getDisplay(
        date: LocalDate?,
        sort: PostSort,
    ): DisplayContent {
        requests += date to sort
        val selectedDate = date ?: LATEST_DATE
        return DisplayContent(
            selectedDate = selectedDate,
            latestDate = LATEST_DATE,
            earliestDate = EARLIEST_DATE,
            topic = if (selectedDate == LATEST_DATE) "바다" else "다리",
            photos = listOf(post),
            featuredPhotos = if (selectedDate < LATEST_DATE) listOf(post, post) else emptyList(),
        )
    }
}

private val post = Post(
    id = "photo",
    imageUrl = "https://example.com/photo.jpg",
    signatureUrl = "https://example.com/signature.png",
    contentDescription = "사진",
    title = "한낮의 다리",
    likeCount = 17,
)
