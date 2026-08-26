package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.domain.model.PostSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockHomeRemoteDataSourceTest {
    private val dataSource = MockHomeRemoteDataSource()

    @Test
    fun `홈 목 응답은 충분한 스크롤을 위해 열두 장의 사진을 제공한다`() = runTest {
        val response = dataSource.getHome(PostSort.LATEST)

        assertEquals(12, response.photos.size)
        assertEquals(
            12,
            response.photos
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals("하늘하늘하늘", response.topic)
    }

    @Test
    fun `좋아요 변경은 다음 홈 응답에도 유지된다`() = runTest {
        val photoId = dataSource
            .getHome(PostSort.LATEST)
            .photos
            .first()
            .id

        val likeResponse = dataSource.updateLike(photoId, isLiked = true)
        val updatedHome = dataSource.getHome(PostSort.LATEST)

        assertEquals(25, likeResponse.likeCount)
        assertTrue(photoId in updatedHome.likedPhotoIds)
        assertEquals(
            25,
            updatedHome.photos
                .first()
                .likeCount,
        )
    }
}
