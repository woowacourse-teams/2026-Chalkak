package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.home.HomeRemoteDataSource
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePhotoResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.PhotoSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRepositoryImplTest {
    private val remoteDataSource = FakeHomeRemoteDataSource()
    private val repository = HomeRepositoryImpl(remoteDataSource)

    @Test
    fun `홈 응답을 도메인 모델로 변환한다`() = runTest {
        val content = repository.getHome(PhotoSort.POPULAR)

        assertEquals(PhotoSort.POPULAR, remoteDataSource.requestedSort)
        assertEquals("하늘하늘하늘", content.topic)
        assertEquals(1, content.photos.size)
        assertEquals(
            "https://example.com/photo.jpg",
            content.photos
                .first()
                .imageUrl,
        )
        assertEquals(setOf("photo-1"), content.likedPhotoIds)
    }

    @Test
    fun `좋아요 응답의 개수를 반환한다`() = runTest {
        val likeCount = repository.updateLike("photo-1", isLiked = true)

        assertEquals("photo-1", remoteDataSource.updatedPhotoId)
        assertTrue(remoteDataSource.updatedIsLiked)
        assertEquals(25, likeCount)
    }
}

private class FakeHomeRemoteDataSource : HomeRemoteDataSource {
    var requestedSort: PhotoSort? = null
    var updatedPhotoId: String? = null
    var updatedIsLiked: Boolean = false

    override suspend fun getHome(sort: PhotoSort): HomeResponse {
        requestedSort = sort
        return HomeResponse(
            dateLabel = "8월 3일 · 오늘의 주제",
            topic = "하늘하늘하늘",
            photos = listOf(
                HomePhotoResponse(
                    id = "photo-1",
                    imageUrl = "https://example.com/photo.jpg",
                    signatureUrl = "https://example.com/signature.png",
                    contentDescription = "하늘",
                    story = "이야기",
                    likeCount = 24,
                ),
            ),
            likedPhotoIds = setOf("photo-1"),
        )
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeLikeResponse {
        updatedPhotoId = photoId
        updatedIsLiked = isLiked
        return HomeLikeResponse(likeCount = 25)
    }
}
