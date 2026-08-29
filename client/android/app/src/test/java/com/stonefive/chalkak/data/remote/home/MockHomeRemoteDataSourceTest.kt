package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockHomeRemoteDataSourceTest {
    private val dataSource = MockHomeRemoteDataSource()

    @Test
    fun `홈 목 응답은 스크롤 가능한 세 장의 사진을 제공한다`() = runTest {
        val response = dataSource.getPosts(homeQuery()).successValue()

        assertEquals(3, response.posts.size)
    }

    @Test
    fun `좋아요 변경은 다음 홈 응답에도 유지된다`() = runTest {
        val photoId = dataSource
            .getPosts(homeQuery())
            .successValue()
            .posts
            .first()
            .id

        val likeResponse = dataSource.updateLike(photoId, isLiked = true).successValue()
        val updatedHome = dataSource.getPosts(homeQuery()).successValue()

        assertEquals(25, likeResponse.likeCount)
        assertTrue(
            updatedHome.posts
                .first()
                .isLiked,
        )
        assertEquals(
            25,
            updatedHome.posts
                .first()
                .likeCount,
        )
    }
}

private fun homeQuery() = HomeQuery(
    date = LocalDate.of(2026, 8, 28),
    sort = PostSort.LATEST,
    page = 1,
)

private fun <T> ApiResult<T>.successValue(): T = (this as ApiResult.Success<T>).value
