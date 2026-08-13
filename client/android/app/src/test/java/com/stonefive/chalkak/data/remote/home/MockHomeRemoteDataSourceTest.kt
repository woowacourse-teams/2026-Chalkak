package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.domain.model.PhotoSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockHomeRemoteDataSourceTest {
    private val dataSource = MockHomeRemoteDataSource()

    @Test
    fun `홈 목 응답은 스크롤 가능한 세 장의 사진을 제공한다`() = runTest {
        val response = dataSource.getHome(PhotoSort.LATEST)

        assertEquals(3, response.photos.size)
        assertEquals("하늘하늘하늘", response.topic)
    }

    @Test
    fun `좋아요 변경은 다음 홈 응답에도 유지된다`() = runTest {
        val photoId = dataSource
            .getHome(PhotoSort.LATEST)
            .photos
            .first()
            .id

        val likeResponse = dataSource.updateLike(photoId, isLiked = true)
        val updatedHome = dataSource.getHome(PhotoSort.LATEST)

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
