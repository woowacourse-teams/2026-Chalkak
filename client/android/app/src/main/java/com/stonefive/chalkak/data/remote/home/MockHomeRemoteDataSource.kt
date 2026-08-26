package com.stonefive.chalkak.data.remote.home

import androidx.annotation.DrawableRes
import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePhotoResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.PostSort
import kotlinx.coroutines.delay

class MockHomeRemoteDataSource(private val responseDelayMillis: Long = 0L) : HomeRemoteDataSource {
    private val likedPhotoIds = mutableSetOf<String>()

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getHome(sort: PostSort): HomeResponse {
        delay(responseDelayMillis)
        return HomeResponse(
            dateLabel = "8월 3일 · 오늘의 주제",
            topic = "하늘하늘하늘",
            photos = listOf(
                HomePhotoResponse(
                    id = FIRST_PHOTO_ID,
                    imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "노을이 진 하늘과 전신주",
                    title = "안녕하세요 찰캌입니다",
                    likeCount = currentLikeCount(FIRST_PHOTO_ID, FIRST_PHOTO_LIKE_COUNT),
                ),
                HomePhotoResponse(
                    id = SECOND_PHOTO_ID,
                    imageUrl = drawableResourceUrl(R.drawable.preview_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "오늘의 주제를 담은 두 번째 사진",
                    title = null,
                    likeCount = currentLikeCount(SECOND_PHOTO_ID, SECOND_PHOTO_LIKE_COUNT),
                ),
                HomePhotoResponse(
                    id = THIRD_PHOTO_ID,
                    imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                    signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                    contentDescription = "저녁 하늘을 담은 세 번째 사진",
                    title = "저녁 하늘",
                    likeCount = currentLikeCount(THIRD_PHOTO_ID, THIRD_PHOTO_LIKE_COUNT),
                ),
            ) + (ADDITIONAL_PHOTO_START..ADDITIONAL_PHOTO_END).map(::additionalHomePhoto),
            likedPhotoIds = likedPhotoIds.toSet(),
        )
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeLikeResponse {
        delay(responseDelayMillis)
        // Feed는 Display 등 다른 소스의 게시물도 열 수 있어 이 Mock이 모르는 id가 올 수 있다.
        // 프로토타입 단계에서는 예외 대신 기본값으로 관대하게 처리한다.
        val defaultLikeCount = defaultLikeCounts[photoId] ?: FALLBACK_LIKE_COUNT
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

    private fun additionalHomePhoto(index: Int): HomePhotoResponse {
        val photoId = photoId(index)
        return HomePhotoResponse(
            id = photoId,
            imageUrl = drawableResourceUrl(
                when (index % 3) {
                    0 -> R.drawable.record_landscape_photo
                    1 -> R.drawable.home_feed_photo
                    else -> R.drawable.preview_photo
                },
            ),
            signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
            contentDescription = "오늘의 주제를 담은 ${index}번째 사진",
            title = if (index % 3 == 0) "오늘의 하늘 $index" else null,
            likeCount = currentLikeCount(photoId, defaultLikeCounts.getValue(photoId)),
        )
    }

    private fun drawableResourceUrl(@DrawableRes resourceId: Int): String =
        "android.resource://com.stonefive.chalkak/$resourceId"

    private companion object {
        const val FIRST_PHOTO_ID = "sample-home-photo-1"
        const val SECOND_PHOTO_ID = "sample-home-photo-2"
        const val THIRD_PHOTO_ID = "sample-home-photo-3"
        const val FIRST_PHOTO_LIKE_COUNT = 24
        const val SECOND_PHOTO_LIKE_COUNT = 12
        const val THIRD_PHOTO_LIKE_COUNT = 31
        const val ADDITIONAL_PHOTO_START = 4
        const val ADDITIONAL_PHOTO_END = 12
        const val FALLBACK_LIKE_COUNT = 0

        fun photoId(index: Int): String = "sample-home-photo-$index"

        val defaultLikeCounts = mapOf(
            FIRST_PHOTO_ID to FIRST_PHOTO_LIKE_COUNT,
            SECOND_PHOTO_ID to SECOND_PHOTO_LIKE_COUNT,
            THIRD_PHOTO_ID to THIRD_PHOTO_LIKE_COUNT,
        ) + (ADDITIONAL_PHOTO_START..ADDITIONAL_PHOTO_END).associate { index ->
            photoId(index) to 8 + index
        }
    }
}
