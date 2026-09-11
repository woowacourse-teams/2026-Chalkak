package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSource
import com.stonefive.chalkak.data.remote.post.model.PostCalendarItemResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarResponse
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostResponse
import com.stonefive.chalkak.data.remote.post.model.TodayPostResponse
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSource
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val topicRemoteDataSource: TopicRemoteDataSource,
) : PostRepository,
    PhotoUploadEntryRepository {
    override suspend fun getTodayPostStatus(): TodayPostStatusResult =
        when (val result = remoteDataSource.getTodayPostStatus()) {
            is ApiResult.Success -> result.value.toDomain()
            is ApiResult.Failure -> TodayPostStatusResult.Failure(result.error.toTodayPostStatusFailure())
        }

    override suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar> =
        when (val result = remoteDataSource.getPostCalendar(month)) {
            is ApiResult.Success -> result.value.toDomain(month)
            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }

    override suspend fun getPostDetail(postId: String): HomeResult<PostDetail> =
        when (val result = remoteDataSource.getPostDetail(postId)) {
            is ApiResult.Success -> result.value.toDomain(postId)
            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }

    override suspend fun deletePost(postId: String): HomeResult<Unit> =
        when (val result = remoteDataSource.deletePost(postId)) {
            is ApiResult.Success -> HomeResult.Success(Unit)
            is ApiResult.Failure -> HomeResult.Failure(result.error.toDomain())
        }

    override suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent> {
        val topic = when (val result = topicRemoteDataSource.getTopic(query.date)) {
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

    private fun PostCalendarResponse.toDomain(requestedMonth: YearMonth): HomeResult<PostCalendar> {
        val responseMonth = runCatching { YearMonth.of(year, month) }.getOrNull()
            ?: return HomeResult.Failure(HomeFailure.InvalidResponse)
        if (responseMonth != requestedMonth) return HomeResult.Failure(HomeFailure.InvalidResponse)

        val mappedPosts = buildList {
            for (post in posts) {
                add(post.toCalendarDomain(responseMonth) ?: return HomeResult.Failure(HomeFailure.InvalidResponse))
            }
        }
        if (
            mappedPosts
                .map(PostCalendarItem::topicDate)
                .distinct()
                .size != mappedPosts.size
        ) {
            return HomeResult.Failure(HomeFailure.InvalidResponse)
        }

        return HomeResult.Success(
            PostCalendar(
                month = responseMonth,
                posts = mappedPosts.sortedBy(PostCalendarItem::topicDate),
            ),
        )
    }

    private fun PostCalendarItemResponse.toCalendarDomain(month: YearMonth): PostCalendarItem? {
        val date = runCatching { LocalDate.parse(topicDate) }.getOrNull() ?: return null
        if (YearMonth.from(date) != month || postId.isBlank() || thumbnailImageUrl.isBlank()) return null

        return PostCalendarItem(
            postId = postId,
            topicDate = date,
            thumbnailImageUrl = thumbnailImageUrl,
            status = runCatching { PostStatus.valueOf(status.uppercase()) }
                .getOrDefault(PostStatus.UNKNOWN),
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
            isOwnedByCurrentUser = isMine,
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
                    isOwnedByCurrentUser = isMine,
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

    private fun TodayPostResponse.toDomain(): TodayPostStatusResult {
        val parsedTopicDate = runCatching { LocalDate.parse(topicDate) }.getOrNull()
            ?: return TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse)

        if (!isPosted) {
            if (postId != null || moderationStatus != null) {
                return TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse)
            }
            return TodayPostStatusResult.Success(
                TodayPostStatus(
                    topicDate = parsedTopicDate,
                    isPosted = false,
                    postId = null,
                    moderationStatus = null,
                ),
            )
        }

        val mappedPostId = postId?.takeIf(String::isNotBlank)
            ?: return TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse)
        val mappedModerationStatus = moderationStatus
            ?.let { status ->
                runCatching { TodayPostModerationStatus.valueOf(status.uppercase()) }
                    .getOrNull()
            }
            ?: return TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse)

        return TodayPostStatusResult.Success(
            TodayPostStatus(
                topicDate = parsedTopicDate,
                isPosted = true,
                postId = mappedPostId,
                moderationStatus = mappedModerationStatus,
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

    private fun ApiError.toTodayPostStatusFailure(): TodayPostStatusFailure = when (this) {
        ApiError.Network -> TodayPostStatusFailure.Network

        ApiError.InvalidResponse -> TodayPostStatusFailure.InvalidResponse

        is ApiError.Http -> when (statusCode) {
            NO_OPEN_TOPIC_STATUS -> TodayPostStatusFailure.NoOpenTopic
            UNAUTHORIZED_STATUS -> TodayPostStatusFailure.ReauthenticationRequired
            SUSPENDED_STATUS -> TodayPostStatusFailure.Suspended
            else -> TodayPostStatusFailure.Http(statusCode)
        }
    }

    private companion object {
        const val TOPIC_NOT_FOUND_STATUS = 404
        const val NO_OPEN_TOPIC_STATUS = 404
        const val UNAUTHORIZED_STATUS = 401
        const val SUSPENDED_STATUS = 403
    }
}
