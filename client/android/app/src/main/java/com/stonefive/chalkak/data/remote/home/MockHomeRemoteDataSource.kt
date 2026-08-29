package com.stonefive.chalkak.data.remote.home

import androidx.annotation.DrawableRes
import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostResponse
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import kotlinx.coroutines.delay

class MockHomeRemoteDataSource(private val responseDelayMillis: Long = 0L) : HomeRemoteDataSource {
    private val likedPhotoIds = mutableSetOf<String>()

    override suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse> {
        delay(responseDelayMillis)
        val post = (
            getPosts(
                HomeQuery(
                    date = LocalDate.of(2026, 8, 29),
                    sort = PostSort.LATEST,
                    page = HomeQuery.FIRST_PAGE,
                ),
            ) as ApiResult.Success
            ).value.posts.firstOrNull { it.id == postId }
            ?: return ApiResult.Failure(ApiError.Http(404, "POST_NOT_FOUND"))

        return ApiResult.Success(
            PostDetailResponse(
                id = post.id,
                topic = TopicResponse(
                    id = "sample-home-topic",
                    title = "하늘하늘하늘",
                    topicDate = "2026-08-29",
                ),
                originalImageUrl = post.originalImageUrl,
                thumbnailImageUrl = post.thumbnailImageUrl,
                signatureOriginalImageUrl = post.signatureOriginalImageUrl,
                title = post.title,
                likeCount = post.likeCount,
                isLiked = post.id in likedPhotoIds,
            ),
        )
    }

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> {
        delay(responseDelayMillis)
        return ApiResult.Success(
            TopicResponse(
                id = "sample-home-topic",
                title = "하늘하늘하늘",
                topicDate = date.toString(),
            ),
        )
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse> {
        delay(responseDelayMillis)
        val posts = listOf(
            PostResponse(
                id = FIRST_PHOTO_ID,
                originalImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                title = "안녕하세요 찰캌입니다",
                likeCount = currentLikeCount(FIRST_PHOTO_ID, FIRST_PHOTO_LIKE_COUNT).toLong(),
                isLiked = FIRST_PHOTO_ID in likedPhotoIds,
            ),
            PostResponse(
                id = SECOND_PHOTO_ID,
                originalImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                thumbnailImageUrl = drawableResourceUrl(R.drawable.preview_photo),
                signatureOriginalImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                signatureThumbnailImageUrl = drawableResourceUrl(R.drawable.preview_signature),
                title = null,
                likeCount = currentLikeCount(SECOND_PHOTO_ID, SECOND_PHOTO_LIKE_COUNT).toLong(),
                isLiked = SECOND_PHOTO_ID in likedPhotoIds,
            ),
            PostResponse(
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
            PostPageResponse(
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
    ): ApiResult<PostLikeResponse> {
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
            PostLikeResponse(
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
