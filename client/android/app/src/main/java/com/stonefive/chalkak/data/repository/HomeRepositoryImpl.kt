package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.home.HomeRemoteDataSource
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostPageResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostResponse
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
import java.time.LocalDate
import java.time.format.DateTimeParseException

class HomeRepositoryImpl(private val remoteDataSource: HomeRemoteDataSource) : HomeRepository {
    override suspend fun getHome(query: HomeQuery): HomeResult<PostContent> {
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
                        dateLabel = "${topicDate.monthValue}월 ${topicDate.dayOfMonth}일 · 오늘의 주제",
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

    private fun HomePostPageResponse.toDomain(query: HomeQuery): HomeResult<PostPage> {
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
                    .filter(HomePostResponse::isLiked)
                    .mapTo(mutableSetOf(), HomePostResponse::id),
                currentPage = currentPage,
                hasNext = hasNext,
                randomSeed = effectiveSeed,
            ),
        )
    }

    private fun HomePostResponse.toDomain(): Post? {
        val mappedLikeCount = likeCount.toLikeCountOrNull() ?: return null
        return Post(
            id = id,
            imageUrl = thumbnailImageUrl ?: originalImageUrl,
            signatureUrl = signatureThumbnailImageUrl ?: signatureOriginalImageUrl,
            contentDescription = title
                ?.takeIf(String::isNotBlank)
                ?.let { "작품 이미지: $it" }
                ?: "무제 작품 이미지",
            title = title,
            likeCount = mappedLikeCount,
        )
    }

    private fun HomeLikeResponse.toDomain(): HomeResult<HomeLike> {
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
