package com.stonefive.chalkak.data.remote.display

import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDisplayRemoteDataSourceTest {
    private val dataSource = MockDisplayRemoteDataSource()

    @Test
    fun `날짜가 없으면 최신 전시를 반환한다`() = runTest {
        val response = dataSource.getDisplay(
            date = null,
            sort = PostSort.LATEST,
        )

        assertEquals("2026-08-05", response.selectedDate)
        assertEquals("바다", response.topic)
        assertTrue(response.featuredPhotos.isEmpty())
        assertEquals(12, response.photos.size)
        assertEquals(
            12,
            response.photos
                .map { it.id }
                .distinct()
                .size,
        )
        assertEquals(
            12,
            response.photos
                .map { it.contentDescription }
                .distinct()
                .size,
        )
    }

    @Test
    fun `과거 날짜는 인기 사진 목록을 함께 반환한다`() = runTest {
        val response = dataSource.getDisplay(
            date = LocalDate.of(2026, 8, 4),
            sort = PostSort.LATEST,
        )

        assertEquals("다리", response.topic)
        assertEquals(5, response.featuredPhotos.size)
        assertEquals(
            response.featuredPhotos
                .map { it.likeCount }
                .sortedDescending(),
            response.featuredPhotos.map { it.likeCount },
        )
    }

    @Test
    fun `인기순은 좋아요 수가 높은 사진부터 반환한다`() = runTest {
        val response = dataSource.getDisplay(
            date = null,
            sort = PostSort.POPULAR,
        )

        assertEquals(
            response.photos
                .map { it.likeCount }
                .sortedDescending(),
            response.photos.map { it.likeCount },
        )
    }
}
