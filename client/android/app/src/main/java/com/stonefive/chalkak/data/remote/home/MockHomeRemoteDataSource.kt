package com.stonefive.chalkak.data.remote.home

import androidx.annotation.DrawableRes
import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostPageResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostResponse
import com.stonefive.chalkak.data.remote.home.model.HomeTopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import java.time.LocalDate
import kotlinx.coroutines.delay

class MockHomeRemoteDataSource(private val responseDelayMillis: Long = 0L) : HomeRemoteDataSource {
    private val likedPhotoIds = mutableSetOf<String>()

    override suspend fun getTopic(date: LocalDate): ApiResult<HomeTopicResponse> {
        delay(responseDelayMillis)
        return ApiResult.Success(
            HomeTopicResponse(
                id = "sample-home-topic",
                title = "하늘하늘하늘",
                topicDate = date.toString(),
            ),
        )
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<HomePostPageResponse> {
        delay(responseDelayMillis)
        val posts = listOf(
            HomePostResponse(
                id = FIRST_PHOTO_ID,
                originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                title = "안녕하세요 찰캌입니다",
                likeCount = currentLikeCount(FIRST_PHOTO_ID, FIRST_PHOTO_LIKE_COUNT).toLong(),
                isLiked = FIRST_PHOTO_ID in likedPhotoIds,
            ),
            HomePostResponse(
                id = SECOND_PHOTO_ID,
                originalImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                title = null,
                likeCount = currentLikeCount(SECOND_PHOTO_ID, SECOND_PHOTO_LIKE_COUNT).toLong(),
                isLiked = SECOND_PHOTO_ID in likedPhotoIds,
            ),
            HomePostResponse(
                id = THIRD_PHOTO_ID,
                originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                title = "저녁 하늘",
                likeCount = currentLikeCount(THIRD_PHOTO_ID, THIRD_PHOTO_LIKE_COUNT).toLong(),
                isLiked = THIRD_PHOTO_ID in likedPhotoIds,
            ),
        )
        return ApiResult.Success(
            HomePostPageResponse(
                currentPage = query.page,
                pageSize = query.pageSize,
                hasNext = false,
                randomSeed = query.randomSeed,
                posts = posts,
            ),
        )
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<HomeLikeResponse> {
        delay(responseDelayMillis)
        // Feed는 Display 등 다른 소스의 게시물도 열 수 있어 이 Mock이 모르는 id가 올 수 있다.
        // 프로토타입 단계에서는 예외 대신 기본값으로 관대하게 처리한다.
        val defaultLikeCount = defaultLikeCounts[photoId] ?: FALLBACK_LIKE_COUNT
        if (isLiked) {
            likedPhotoIds += photoId
        } else {
            likedPhotoIds -= photoId
        }
        return ApiResult.Success(
            HomeLikeResponse(
                postId = photoId,
                likeCount = (defaultLikeCount + if (isLiked) 1 else 0).toLong(),
                isLiked = isLiked,
            ),
        )
    }

    private fun currentLikeCount(
        photoId: String,
        defaultLikeCount: Int,
    ): Int = defaultLikeCount + if (photoId in likedPhotoIds) 1 else 0

    private fun drawableResourceUrl(@DrawableRes resourceId: Int): String =
        "android.resource://com.stonefive.chalkak/$resourceId"

    private companion object {
        const val FIRST_PHOTO_ID = "sample-home-photo-1"
        const val SECOND_PHOTO_ID = "sample-home-photo-2"
        const val THIRD_PHOTO_ID = "sample-home-photo-3"
        const val FIRST_PHOTO_LIKE_COUNT = 24
        const val SECOND_PHOTO_LIKE_COUNT = 12
        const val THIRD_PHOTO_LIKE_COUNT = 31
        const val FALLBACK_LIKE_COUNT = 0

        val defaultLikeCounts = mapOf(
            FIRST_PHOTO_ID to FIRST_PHOTO_LIKE_COUNT,
            SECOND_PHOTO_ID to SECOND_PHOTO_LIKE_COUNT,
            THIRD_PHOTO_ID to THIRD_PHOTO_LIKE_COUNT,
        )
    }
}
