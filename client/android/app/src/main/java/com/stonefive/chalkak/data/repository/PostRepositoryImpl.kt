package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSource
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostResponse
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

class PostRepositoryImpl(private val remoteDataSource: PostRemoteDataSource) : PostRepository {
    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> =
        when (val result = remoteDataSource.getPostDetail(postId)) {
            is ApiResult.Success -> result.value.toDomain(postId)
            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        val topic = when (val result = remoteDataSource.getTopic(query.date)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return HomeResult.Failure(result.error.toDomain(isTopic = true))
        }
        val topicDate = try {
            LocalDate.parse(topic.topicDate)
        } catch (_: DateTimeParseException) {
            return HomeResult.Failure(HomeFailure.InvalidResponse)
        }
        val pageQuery = query.copy(date = topicDate)
        return when (val result = remoteDataSource.getPosts(pageQuery)) {
            is ApiResult.Success -> when (val pageResult = result.value.toDomain(pageQuery)) {
                is HomeResult.Success -> HomeResult.Success(
                    PostContent(
                        topicDate = topicDate,
                        topic = topic.title,
                        photos = pageResult.value.photos,
                        likedPhotoIds = pageResult.value.likedPhotoIds,
                        currentPage = pageResult.value.currentPage,
                        hasNext = pageResult.value.hasNext,
                        randomSeed = pageResult.value.randomSeed,
                    ),
                )

                is HomeResult.Failure -> pageResult
            }

            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }
    }

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> =
        when (val result = remoteDataSource.getPosts(query)) {
            is ApiResult.Success -> result.value.toDomain(query)
            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = when (val result = remoteDataSource.updateLike(photoId, isLiked)) {
        is ApiResult.Success -> if (result.value.postId == photoId) {
            result.value.toDomain()
        } else {
            HomeResult.Failure(HomeFailure.InvalidResponse)
        }

        is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
    }

    private fun PostPageResponse.toDomain(query: HomeQuery): HomeResult<PostPage> {
        val effectiveSeed = if (query.sort == PostSort.RANDOM) {
            randomSeed ?: query.randomSeed
        } else {
            null
        }
        if (query.sort == PostSort.RANDOM && hasNext && effectiveSeed.isNullOrBlank()) {
            return HomeResult.Failure(HomeFailure.InvalidResponse)
        }

        val mappedPosts = buildList {
            for (post in posts) {
                add(post.toDomain() ?: return HomeResult.Failure(HomeFailure.InvalidResponse))
            }
        }
        return HomeResult.Success(
            PostPage(
                photos = mappedPosts,
                likedPhotoIds = posts
                    .filter(PostResponse::isLiked)
                    .mapTo(mutableSetOf(), PostResponse::id),
                currentPage = currentPage,
                hasNext = hasNext,
                randomSeed = effectiveSeed,
            ),
        )
    }

    private fun PostResponse.toDomain(): Post? {
        val mappedLikeCount = likeCount.toLikeCountOrNull() ?: return null
        return Post(
            id = id,
            originalImageUrl = originalImageUrl,
            thumbnailImageUrl = thumbnailImageUrl,
            signatureOriginalImageUrl = signatureOriginalImageUrl,
            signatureThumbnailImageUrl = signatureThumbnailImageUrl,
            contentDescription = title
                ?.takeIf(String::isNotBlank)
                ?.let { "작품 이미지: $it" }
                ?: "무제 작품 이미지",
            submittedAt = submittedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            title = title,
            likeCount = mappedLikeCount,
            isLiked = isLiked,
        )
    }

    private fun PostDetailResponse.toDomain(postId: String): HomeResult<PostDetail> {
        if (id != postId) return HomeResult.Failure(HomeFailure.InvalidResponse)

        val parsedTopicDate = runCatching { LocalDate.parse(topic.topicDate) }.getOrNull()
            ?: return HomeResult.Failure(HomeFailure.InvalidResponse)
        val mappedLikeCount = likeCount.toLikeCountOrNull()
            ?: return HomeResult.Failure(HomeFailure.InvalidResponse)
        if (originalImageUrl.isBlank() || signatureOriginalImageUrl.isBlank()) {
            return HomeResult.Failure(HomeFailure.InvalidResponse)
        }

        return HomeResult.Success(
            PostDetail(
                post = Post(
                    id = id,
                    originalImageUrl = originalImageUrl,
                    thumbnailImageUrl = thumbnailImageUrl,
                    signatureOriginalImageUrl = signatureOriginalImageUrl,
                    signatureThumbnailImageUrl = null,
                    contentDescription = title
                        ?.takeIf(String::isNotBlank)
                        ?.let { "작품 이미지: $it" }
                        ?: "무제 작품 이미지",
                    submittedAt = null,
                    title = title,
                    likeCount = mappedLikeCount,
                    isLiked = isLiked,
                ),
                topic = topic.title,
                topicDate = parsedTopicDate,
            ),
        )
    }

    private fun PostLikeResponse.toDomain(): HomeResult<HomeLike> {
        val mappedLikeCount = likeCount.toLikeCountOrNull()
            ?: return HomeResult.Failure(HomeFailure.InvalidResponse)
        return HomeResult.Success(
            HomeLike(
                likeCount = mappedLikeCount,
                isLiked = isLiked,
            ),
        )
    }

    private fun Long.toLikeCountOrNull(): Int? = takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()

    private fun ApiError.toDomain(isTopic: Boolean = false): HomeFailure = when (this) {
        ApiError.Network -> HomeFailure.Network

        ApiError.InvalidResponse -> HomeFailure.InvalidResponse

        is ApiError.Http -> when {
            isTopic && statusCode == TOPIC_NOT_FOUND_STATUS -> HomeFailure.TopicNotFound
            statusCode == UNAUTHORIZED_STATUS -> HomeFailure.Unauthorized
            else -> HomeFailure.Http(statusCode)
        }
    }

    private companion object {
        const val TOPIC_NOT_FOUND_STATUS = 404
        const val UNAUTHORIZED_STATUS = 401
    }
}
