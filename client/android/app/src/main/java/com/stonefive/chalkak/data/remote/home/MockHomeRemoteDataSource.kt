package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.home.model.HomeImageResponse
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePhotoResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.HomeSort
import kotlinx.coroutines.delay

class MockHomeRemoteDataSource(private val responseDelayMillis: Long = 0L) : HomeRemoteDataSource {
    private val likedPhotoIds = mutableSetOf<String>()

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getHome(sort: HomeSort): HomeResponse {
        delay(responseDelayMillis)
        return HomeResponse(
            dateLabel = "8월 3일 · 오늘의 주제",
            topic = "하늘하늘하늘",
            photos = listOf(
                HomePhotoResponse(
                    id = FIRST_PHOTO_ID,
                    image = HomeImageResponse.Local(R.drawable.home_feed_photo),
                    signatureImage = null,
                    contentDescription = "노을이 진 하늘과 전신주",
                    story = "안녕하세요 감사합니다.",
                    likeCount = currentLikeCount(FIRST_PHOTO_ID, FIRST_PHOTO_LIKE_COUNT),
                ),
                HomePhotoResponse(
                    id = SECOND_PHOTO_ID,
                    image = HomeImageResponse.Local(R.drawable.preview_photo),
                    signatureImage = HomeImageResponse.Local(R.drawable.preview_signature),
                    contentDescription = "오늘의 주제를 담은 두 번째 사진",
                    story = null,
                    likeCount = currentLikeCount(SECOND_PHOTO_ID, SECOND_PHOTO_LIKE_COUNT),
                ),
                HomePhotoResponse(
                    id = THIRD_PHOTO_ID,
                    image = HomeImageResponse.Local(R.drawable.home_feed_photo),
                    signatureImage = null,
                    contentDescription = "저녁 하늘을 담은 세 번째 사진",
                    story = "하루가 저무는 순간을 기록했어요.",
                    likeCount = currentLikeCount(THIRD_PHOTO_ID, THIRD_PHOTO_LIKE_COUNT),
                ),
            ),
            likedPhotoIds = likedPhotoIds.toSet(),
        )
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeLikeResponse {
        delay(responseDelayMillis)
        val defaultLikeCount = defaultLikeCounts[photoId]
            ?: error("Unknown home photo: $photoId")
        if (isLiked) {
            likedPhotoIds += photoId
        } else {
            likedPhotoIds -= photoId
        }
        return HomeLikeResponse(
            likeCount = defaultLikeCount + if (isLiked) 1 else 0,
        )
    }

    private fun currentLikeCount(
        photoId: String,
        defaultLikeCount: Int,
    ): Int = defaultLikeCount + if (photoId in likedPhotoIds) 1 else 0

    private companion object {
        const val FIRST_PHOTO_ID = "sample-home-photo-1"
        const val SECOND_PHOTO_ID = "sample-home-photo-2"
        const val THIRD_PHOTO_ID = "sample-home-photo-3"
        const val FIRST_PHOTO_LIKE_COUNT = 24
        const val SECOND_PHOTO_LIKE_COUNT = 12
        const val THIRD_PHOTO_LIKE_COUNT = 31

        val defaultLikeCounts = mapOf(
            FIRST_PHOTO_ID to FIRST_PHOTO_LIKE_COUNT,
            SECOND_PHOTO_ID to SECOND_PHOTO_LIKE_COUNT,
            THIRD_PHOTO_ID to THIRD_PHOTO_LIKE_COUNT,
        )
    }
}
