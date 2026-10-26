package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSource
import com.stonefive.chalkak.data.remote.post.model.AuthorPostResponse
import com.stonefive.chalkak.data.remote.post.model.AuthorPostsResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarItemResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarMonthsResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarResponse
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostResponse
import com.stonefive.chalkak.data.remote.post.model.PostTitleUpdateResponse
import com.stonefive.chalkak.data.remote.post.model.TodayPostResponse
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSource
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRepositoryImplTest {
    private val remoteDataSource = FakePostRemoteDataSource()
    private val topicRemoteDataSource = FakeHomeTopicRemoteDataSource()
    private val repository = PostRepositoryImpl(remoteDataSource, topicRemoteDataSource)
    private val query = HomeQuery(
        date = LocalDate.of(2026, 8, 28),
        sort = PostSort.LATEST,
        page = 1,
    )

    @Test
    fun `작성자 공개 사진은 페이지와 주제를 매핑하고 썸네일이 없으면 원본을 사용한다`() = runTest {
        remoteDataSource.authorPostsResult = ApiResult.Success(
            authorPostsResponse(),
        )

        val result = repository.getAuthorPosts("author-1", page = 2, pageSize = 20) as HomeResult.Success

        assertEquals(listOf(Triple("author-1", 2, 20)), remoteDataSource.authorPostsQueries)
        assertEquals(2, result.value.currentPage)
        assertEquals(20, result.value.pageSize)
        assertTrue(result.value.hasNext)
        val post = result.value.posts
            .single()
        assertEquals("author-photo", post.id)
        assertEquals("original", post.imageUrl)
        assertEquals(LocalDate.of(2026, 10, 19), post.topic.date)
        assertEquals("주제", post.topic.title)
        assertEquals(23, post.likeCount)
        assertTrue(post.isLiked)
    }

    @Test
    fun `작성자 목록의 좋아요 수는 0부터 Int 최댓값까지만 허용한다`() = runTest {
        val response = authorPostsResponse()
        listOf(0L, Int.MAX_VALUE.toLong()).forEach { count ->
            remoteDataSource.authorPostsResult = ApiResult.Success(
                response.copy(
                    posts = listOf(
                        response.posts
                            .single()
                            .copy(likeCount = count, isLiked = false),
                    ),
                ),
            )
            val result = repository.getAuthorPosts("author-1", 2, 20) as HomeResult.Success
            assertEquals(
                count.toInt(),
                result.value.posts
                    .single()
                    .likeCount,
            )
            assertFalse(
                result.value.posts
                    .single()
                    .isLiked,
            )
        }
        listOf(-1L, Int.MAX_VALUE.toLong() + 1L).forEach { count ->
            remoteDataSource.authorPostsResult = ApiResult.Success(
                response.copy(
                    posts = listOf(
                        response.posts
                            .single()
                            .copy(likeCount = count),
                    ),
                ),
            )
            assertEquals(
                HomeResult.Failure(HomeFailure.InvalidResponse),
                repository.getAuthorPosts("author-1", 2, 20),
            )
        }
    }

    @Test
    fun `작성자 목록의 빈 응답은 성공이며 HTTP 오류는 인증과 찾을 수 없음을 구분한다`() = runTest {
        remoteDataSource.authorPostsResult =
            ApiResult.Success(authorPostsResponse().copy(posts = emptyList(), hasNext = false))
        val result = repository.getAuthorPosts("author-1", 2, 20) as HomeResult.Success
        assertTrue(
            result.value.posts
                .isEmpty(),
        )
        assertFalse(result.value.hasNext)
        listOf(401 to HomeFailure.Unauthorized, 404 to HomeFailure.Http(404)).forEach { (status, failure) ->
            remoteDataSource.authorPostsResult = ApiResult.Failure(ApiError.Http(status, "BUSINESS_ERROR"))
            assertEquals(HomeResult.Failure(failure), repository.getAuthorPosts("author-1"))
        }
    }

    @Test
    fun `작성자 목록의 페이지 불일치와 잘못된 날짜는 InvalidResponse다`() = runTest {
        val response = authorPostsResponse()
        listOf(
            response.copy(currentPage = 3),
            response.copy(pageSize = 10),
            response.copy(posts = response.posts.map { it.copy(topic = it.topic.copy(topicDate = "invalid")) }),
        ).forEach { invalid ->
            remoteDataSource.authorPostsResult = ApiResult.Success(invalid)
            assertEquals(HomeResult.Failure(HomeFailure.InvalidResponse), repository.getAuthorPosts("author-1", 2, 20))
        }
    }

    @Test
    fun `피드와 상세의 작성자 ID를 조회자나 게시물 ID와 구분해 매핑한다`() = runTest {
        val content = (repository.getPostContent(query) as HomeResult.Success).value
        assertEquals(
            "author-photo-1",
            content.photos
                .first()
                .authorId,
        )
    }

    @Test
    fun `오늘 작성 여부 true 응답은 허용된 moderation status와 postId를 보존한다`() = runTest {
        listOf(
            "VALIDATING" to TodayPostModerationStatus.VALIDATING,
            "PENDING" to TodayPostModerationStatus.PENDING,
            "APPROVED" to TodayPostModerationStatus.APPROVED,
        ).forEach { (responseStatus, expectedStatus) ->
            remoteDataSource.todayPostResult = ApiResult.Success(
                TodayPostResponse(
                    topicDate = "2026-09-09",
                    isPosted = true,
                    postId = "post-id",
                    moderationStatus = responseStatus,
                ),
            )

            assertEquals(
                TodayPostStatusResult.Success(
                    TodayPostStatus(
                        topicDate = LocalDate.of(2026, 9, 9),
                        isPosted = true,
                        postId = "post-id",
                        moderationStatus = expectedStatus,
                    ),
                ),
                repository.getTodayPostStatus(),
            )
        }
    }

    @Test
    fun `오늘 작성 여부 false 응답은 postId와 moderationStatus가 null이어야 한다`() = runTest {
        remoteDataSource.todayPostResult = ApiResult.Success(
            TodayPostResponse(
                topicDate = "2026-09-09",
                isPosted = false,
                postId = null,
                moderationStatus = null,
            ),
        )

        assertEquals(
            TodayPostStatusResult.Success(
                TodayPostStatus(
                    topicDate = LocalDate.of(2026, 9, 9),
                    isPosted = false,
                    postId = null,
                    moderationStatus = null,
                ),
            ),
            repository.getTodayPostStatus(),
        )

        listOf(
            TodayPostResponse("2026-09-09", isPosted = false, postId = "post-id"),
            TodayPostResponse("2026-09-09", isPosted = false, moderationStatus = "REJECTED"),
        ).forEach { invalidResponse ->
            remoteDataSource.todayPostResult = ApiResult.Success(invalidResponse)

            assertEquals(
                TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse),
                repository.getTodayPostStatus(),
            )
        }
    }

    @Test
    fun `오늘 작성 여부 응답 계약 위반은 InvalidResponse다`() = runTest {
        listOf(
            TodayPostResponse("2026-02-30", isPosted = false),
            TodayPostResponse("2026-09-09", isPosted = true, postId = "", moderationStatus = "VALIDATING"),
            TodayPostResponse("2026-09-09", isPosted = true, postId = "post-id", moderationStatus = null),
            TodayPostResponse("2026-09-09", isPosted = true, postId = "post-id", moderationStatus = "REJECTED"),
        ).forEach { invalidResponse ->
            remoteDataSource.todayPostResult = ApiResult.Success(invalidResponse)

            assertEquals(
                TodayPostStatusResult.Failure(TodayPostStatusFailure.InvalidResponse),
                repository.getTodayPostStatus(),
            )
        }
    }

    @Test
    fun `오늘 작성 여부 HTTP 실패를 진입 게이트 실패로 구분한다`() = runTest {
        listOf(
            ApiError.Http(404, "TOPIC_NOT_FOUND") to TodayPostStatusFailure.NoOpenTopic,
            ApiError.Http(401, "UNAUTHORIZED") to TodayPostStatusFailure.ReauthenticationRequired,
            ApiError.Http(403, "SUSPENDED") to TodayPostStatusFailure.Suspended,
            ApiError.Http(500, "SERVER_ERROR") to TodayPostStatusFailure.Http(500),
            ApiError.Network to TodayPostStatusFailure.Network,
            ApiError.InvalidResponse to TodayPostStatusFailure.InvalidResponse,
        ).forEach { (apiError, expectedFailure) ->
            remoteDataSource.todayPostResult = ApiResult.Failure(apiError)

            assertEquals(
                TodayPostStatusResult.Failure(expectedFailure),
                repository.getTodayPostStatus(),
            )
        }
    }

    @Test
    fun `게시물 캘린더 응답을 날짜순 도메인 모델로 변환한다`() = runTest {
        remoteDataSource.calendarResult = ApiResult.Success(
            calendarResponse(
                posts = listOf(
                    calendarPost(day = 31, status = "APPROVED"),
                    calendarPost(day = 2, status = "NEW_SERVER_STATUS"),
                ),
            ),
        )

        val result = repository.getPostCalendar(YearMonth.of(2026, 8)) as HomeResult.Success<PostCalendar>

        assertEquals(
            listOf(2, 31),
            result.value.posts
                .map { it.topicDate.dayOfMonth },
        )
        assertEquals(
            PostStatus.UNKNOWN,
            result.value.posts[0]
                .status,
        )
        assertEquals(
            PostStatus.APPROVED,
            result.value.posts[1]
                .status,
        )
    }

    @Test
    fun `요청 월과 다른 캘린더 응답은 InvalidResponse다`() = runTest {
        remoteDataSource.calendarResult = ApiResult.Success(calendarResponse(month = 7))

        assertEquals(
            HomeResult.Failure(HomeFailure.InvalidResponse),
            repository.getPostCalendar(YearMonth.of(2026, 8)),
        )
    }

    @Test
    fun `게시물 상세 응답을 Feed 도메인 모델로 변환한다`() = runTest {
        remoteDataSource.detailResult = ApiResult.Success(
            PostDetailResponse(
                id = "photo-1",
                authorId = "author-1",
                topic = TopicResponse(
                    id = "topic-1",
                    title = "새 바다",
                    topicDate = "2026-08-29",
                ),
                originalImageUrl = "photo-original",
                thumbnailImageUrl = "photo-thumbnail",
                signatureOriginalImageUrl = "signature-original",
                title = "상세 제목",
                likeCount = 42,
                isLiked = true,
                isMine = true,
            ),
        )

        val result = repository.getPostDetail("photo-1") as HomeResult.Success<PostDetail>

        assertEquals(
            PostDetail(
                post = Post(
                    id = "photo-1",
                    authorId = "author-1",
                    originalImageUrl = "photo-original",
                    thumbnailImageUrl = "photo-thumbnail",
                    signatureOriginalImageUrl = "signature-original",
                    signatureThumbnailImageUrl = null,
                    contentDescription = "작품 이미지: 상세 제목",
                    title = "상세 제목",
                    likeCount = 42,
                    isLiked = true,
                    isOwnedByCurrentUser = true,
                ),
                topic = "새 바다",
                topicDate = LocalDate.of(2026, 8, 29),
            ),
            result.value,
        )
    }

    @Test
    fun `게시물 삭제 성공과 권한 실패를 도메인 결과로 변환한다`() = runTest {
        remoteDataSource.deleteResult = ApiResult.Success(Unit)
        assertEquals(HomeResult.Success(Unit), repository.deletePost("photo-1"))

        remoteDataSource.deleteResult = ApiResult.Failure(ApiError.Http(403, "FORBIDDEN"))
        assertEquals(
            HomeResult.Failure(HomeFailure.Http(403)),
            repository.deletePost("photo-1"),
        )
    }

    @Test
    fun `게시물 제목은 앞뒤 공백을 제거하고 빈 제목은 null로 변환한다`() = runTest {
        remoteDataSource.titleUpdateResult = ApiResult.Success(
            PostTitleUpdateResponse("photo-1", "수정한 제목"),
        )

        val result = repository.updatePostTitle("photo-1", "  수정한 제목  ") as HomeResult.Success

        assertEquals("photo-1" to "수정한 제목", remoteDataSource.updatedTitle)
        assertEquals("수정한 제목", result.value.title)

        remoteDataSource.titleUpdateResult = ApiResult.Success(
            PostTitleUpdateResponse("photo-1", null),
        )
        val cleared = repository.updatePostTitle("photo-1", "   ") as HomeResult.Success

        assertEquals("photo-1" to null, remoteDataSource.updatedTitle)
        assertEquals(null, cleared.value.title)
    }

    @Test
    fun `토픽과 첫 게시물 페이지를 Home 도메인 콘텐츠로 변환한다`() = runTest {
        topicRemoteDataSource.result = ApiResult.Success(
            TopicResponse(
                id = "new-topic-id",
                title = "새 바다",
                topicDate = "2026-08-29",
            ),
        )

        val result = repository.getPostContent(query) as HomeResult.Success
        val content = result.value

        assertEquals(
            LocalDate.of(2026, 8, 29),
            remoteDataSource.postsQueries
                .single()
                .date,
        )
        assertEquals(LocalDate.of(2026, 8, 29), content.topicDate)
        assertEquals("새 바다", content.topic)
        assertEquals(listOf("photo-1", "photo-2", "photo-3"), content.photos.map { it.id })
        assertEquals(setOf("photo-1"), content.likedPhotoIds)
        assertEquals(1, content.currentPage)
        assertTrue(content.hasNext)
        assertEquals(null, content.randomSeed)
    }

    @Test
    fun `게시물과 사인 썸네일 URL을 직접 매핑한다`() = runTest {
        val content = (repository.getPostContent(query) as HomeResult.Success).value

        assertEquals("photo-original", content.photos[0].originalImageUrl)
        assertEquals("photo-thumbnail", content.photos[0].thumbnailImageUrl)
        assertEquals("signature-original", content.photos[0].signatureOriginalImageUrl)
        assertEquals("signature-thumbnail", content.photos[0].signatureThumbnailImageUrl)
        assertEquals(Instant.parse("2026-08-28T01:00:00Z"), content.photos[0].submittedAt)
        assertTrue(content.photos[0].isOwnedByCurrentUser)
        assertEquals("photo-original-2", content.photos[1].originalImageUrl)
        assertEquals("photo-thumbnail-2", content.photos[1].thumbnailImageUrl)
        assertEquals("signature-original-2", content.photos[1].signatureOriginalImageUrl)
        assertEquals("signature-thumbnail", content.photos[1].signatureThumbnailImageUrl)
        assertFalse(content.photos[1].isOwnedByCurrentUser)
    }

    @Test
    fun `목록 응답의 isMine을 게시물 소유권으로 매핑한다`() = runTest {
        remoteDataSource.postsResult = ApiResult.Success(
            postPage(
                posts = listOf(
                    homePost(id = "owned", title = null, isMine = true),
                    homePost(id = "other", title = null, isMine = false),
                ),
            ),
        )

        val content = (repository.getPostContent(query) as HomeResult.Success).value

        assertTrue(content.photos[0].isOwnedByCurrentUser)
        assertFalse(content.photos[1].isOwnedByCurrentUser)
    }

    @Test
    fun `제목 접근성 문구는 nonblank blank null을 모두 매핑한다`() = runTest {
        val content = (repository.getPostContent(query) as HomeResult.Success).value

        assertEquals("작품 이미지: 바다", content.photos[0].contentDescription)
        assertEquals("무제 작품 이미지", content.photos[1].contentDescription)
        assertEquals("무제 작품 이미지", content.photos[2].contentDescription)
    }

    @Test
    fun `HTTP 200 빈 게시물은 성공한 빈 콘텐츠다`() = runTest {
        topicRemoteDataSource.result = ApiResult.Success(
            TopicResponse(
                id = "new-topic-id",
                title = "새 주제",
                topicDate = "2026-08-29",
            ),
        )
        remoteDataSource.postsResult = ApiResult.Success(postPage(posts = emptyList()))

        val result = repository.getPostContent(query) as HomeResult.Success

        assertEquals(LocalDate.of(2026, 8, 29), result.value.topicDate)
        assertEquals("새 주제", result.value.topic)
        assertTrue(
            result.value.photos
                .isEmpty(),
        )
        assertFalse(result.value.hasNext)
    }

    @Test
    fun `topicDate 파싱 실패는 InvalidResponse로 분류하고 posts를 요청하지 않는다`() = runTest {
        topicRemoteDataSource.result = ApiResult.Success(
            TopicResponse(
                id = "invalid-topic-id",
                title = "잘못된 주제",
                topicDate = "2026-02-30",
            ),
        )

        assertEquals(
            HomeResult.Failure(HomeFailure.InvalidResponse),
            repository.getPostContent(query),
        )
        assertTrue(remoteDataSource.postsQueries.isEmpty())
    }

    @Test
    fun `RANDOM 첫 페이지에 next가 있는데 seed가 없거나 빈 문자열이면 InvalidResponse다`() = runTest {
        listOf(null, "", "   ").forEach { seed ->
            remoteDataSource.postsResult = ApiResult.Success(
                postPage(
                    hasNext = true,
                    randomSeed = seed,
                ),
            )

            assertEquals(
                HomeResult.Failure(HomeFailure.InvalidResponse),
                repository.getPostContent(query.copy(sort = PostSort.RANDOM)),
            )
        }
    }

    @Test
    fun `RANDOM 다음 페이지 응답에 seed가 없으면 요청 seed를 유지한다`() = runTest {
        remoteDataSource.postsResult = ApiResult.Success(
            postPage(
                hasNext = true,
                randomSeed = null,
            ),
        )
        val pageQuery = query.copy(
            sort = PostSort.RANDOM,
            page = 2,
            randomSeed = "existing-seed",
        )

        val result = repository.getPostPage(pageQuery) as HomeResult.Success

        assertEquals("existing-seed", result.value.randomSeed)
    }

    @Test
    fun `non RANDOM 응답 seed는 domain에서 폐기한다`() = runTest {
        remoteDataSource.postsResult = ApiResult.Success(postPage(randomSeed = "server-seed"))

        val result = repository.getPostPage(query) as HomeResult.Success

        assertEquals(null, result.value.randomSeed)
    }

    @Test
    fun `게시물 likeCount는 Int 범위만 허용한다`() = runTest {
        remoteDataSource.postsResult = ApiResult.Success(
            postPage(posts = listOf(homePost("max", null, likeCount = Int.MAX_VALUE.toLong()))),
        )
        val success = repository.getPostContent(query) as HomeResult.Success
        assertEquals(
            Int.MAX_VALUE,
            success.value.photos
                .single()
                .likeCount,
        )

        listOf(Int.MAX_VALUE.toLong() + 1L, -1L).forEach { invalidCount ->
            remoteDataSource.postsResult = ApiResult.Success(
                postPage(posts = listOf(homePost("invalid", null, likeCount = invalidCount))),
            )
            assertEquals(
                HomeResult.Failure(HomeFailure.InvalidResponse),
                repository.getPostContent(query),
            )
        }
    }

    @Test
    fun `좋아요 응답의 count와 state를 권위 있는 결과로 매핑한다`() = runTest {
        remoteDataSource.likeResult = ApiResult.Success(
            PostLikeResponse(
                postId = "photo-1",
                likeCount = Int.MAX_VALUE.toLong(),
                isLiked = true,
            ),
        )
        val result = repository.updateLike("photo-1", isLiked = true) as HomeResult.Success

        assertEquals(Int.MAX_VALUE, result.value.likeCount)
        assertTrue(result.value.isLiked)
    }

    @Test
    fun `좋아요 응답 count가 Int 범위를 벗어나면 InvalidResponse다`() = runTest {
        listOf(Int.MAX_VALUE.toLong() + 1L, -1L).forEach { invalidCount ->
            remoteDataSource.likeResult = ApiResult.Success(
                PostLikeResponse(
                    postId = "photo-1",
                    likeCount = invalidCount,
                    isLiked = true,
                ),
            )
            assertEquals(
                HomeResult.Failure(HomeFailure.InvalidResponse),
                repository.updateLike("photo-1", isLiked = true),
            )
        }
    }

    @Test
    fun `좋아요 응답 postId가 요청한 게시물과 다르면 InvalidResponse다`() = runTest {
        remoteDataSource.likeResult = ApiResult.Success(
            PostLikeResponse(
                postId = "other-photo",
                likeCount = 25,
                isLiked = true,
            ),
        )

        assertEquals(
            HomeResult.Failure(HomeFailure.InvalidResponse),
            repository.updateLike("photo-1", isLiked = true),
        )
    }

    @Test
    fun `성공한 좋아요 응답만 게시물 ID와 함께 변경을 공유한다`() = runTest {
        val updates = mutableListOf<PostLikeUpdate>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.likeUpdates.toList(updates)
        }
        remoteDataSource.likeResult = ApiResult.Failure(ApiError.Network)
        repository.updateLike("photo-1", true)
        remoteDataSource.likeResult = ApiResult.Success(PostLikeResponse("other", 25, true))
        repository.updateLike("photo-1", true)
        remoteDataSource.likeResult = ApiResult.Success(PostLikeResponse("photo-1", -1, true))
        repository.updateLike("photo-1", true)
        yield()
        assertTrue(updates.isEmpty())

        remoteDataSource.likeResult = ApiResult.Success(PostLikeResponse("photo-1", 25, true))
        repository.updateLike("photo-1", true)
        yield()
        assertEquals(listOf(PostLikeUpdate("photo-1", 25, true)), updates)

        remoteDataSource.likeResult = ApiResult.Success(PostLikeResponse("photo-1", 24, false))
        repository.updateLike("photo-1", false)
        yield()
        assertEquals(PostLikeUpdate("photo-1", 24, false), updates.last())
    }

    @Test
    fun `같은 게시물의 늦은 응답은 공유하지 않고 다른 게시물의 변경은 공유한다`() = runTest {
        val updates = mutableListOf<PostLikeUpdate>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.likeUpdates.toList(updates)
        }
        val older = CompletableDeferred<ApiResult<PostLikeResponse>>()
        val newer = CompletableDeferred<ApiResult<PostLikeResponse>>()
        val other = CompletableDeferred<ApiResult<PostLikeResponse>>()
        remoteDataSource.pendingLikes.addAll(listOf(older, newer, other))
        val olderRequest = async(start = CoroutineStart.UNDISPATCHED) { repository.updateLike("photo-1", true) }
        val newerRequest = async(start = CoroutineStart.UNDISPATCHED) { repository.updateLike("photo-1", false) }
        val otherRequest = async(start = CoroutineStart.UNDISPATCHED) { repository.updateLike("photo-2", true) }

        newer.complete(ApiResult.Success(PostLikeResponse("photo-1", 24, false)))
        newerRequest.await()
        other.complete(ApiResult.Success(PostLikeResponse("photo-2", 10, true)))
        otherRequest.await()
        older.complete(ApiResult.Success(PostLikeResponse("photo-1", 25, true)))
        val olderResult = olderRequest.await() as HomeResult.Success
        yield()

        assertEquals(25, olderResult.value.likeCount)
        assertEquals(
            listOf(PostLikeUpdate("photo-1", 24, false), PostLikeUpdate("photo-2", 10, true)),
            updates,
        )
    }

    @Test
    fun `새 구독자에게 이전 좋아요 변경을 재전달하지 않는다`() = runTest {
        repository.updateLike("photo-1", true)
        val updates = mutableListOf<PostLikeUpdate>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.likeUpdates.toList(updates)
        }
        yield()

        assertTrue(updates.isEmpty())
    }

    @Test
    fun `토픽 404와 인증 네트워크 응답을 도메인 실패로 구분한다`() = runTest {
        topicRemoteDataSource.result = ApiResult.Failure(ApiError.Http(404, "TOPIC_NOT_FOUND"))
        assertEquals(HomeResult.Failure(HomeFailure.TopicNotFound), repository.getPostContent(query))

        topicRemoteDataSource.result = ApiResult.Failure(ApiError.Http(401, "UNAUTHORIZED"))
        assertEquals(HomeResult.Failure(HomeFailure.Unauthorized), repository.getPostContent(query))

        topicRemoteDataSource.result = ApiResult.Failure(ApiError.Network)
        assertEquals(HomeResult.Failure(HomeFailure.Network), repository.getPostContent(query))
    }
}

private class FakePostRemoteDataSource : PostRemoteDataSource {
    var authorPostsResult: ApiResult<AuthorPostsResponse> =
        ApiResult.Failure(ApiError.Network)
    val authorPostsQueries = mutableListOf<Triple<String, Int, Int>>()

    override suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<AuthorPostsResponse> {
        authorPostsQueries += Triple(authorId, page, pageSize)
        return authorPostsResult
    }

    val postsQueries = mutableListOf<HomeQuery>()
    var todayPostResult: ApiResult<TodayPostResponse> = ApiResult.Success(
        TodayPostResponse(
            topicDate = "2026-09-09",
            isPosted = false,
        ),
    )
    var detailResult: ApiResult<PostDetailResponse> = ApiResult.Failure(ApiError.Network)
    var calendarResult: ApiResult<PostCalendarResponse> = ApiResult.Success(calendarResponse())
    var postsResult: ApiResult<PostPageResponse> = ApiResult.Success(postPage())
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var titleUpdateResult: ApiResult<PostTitleUpdateResponse> = ApiResult.Failure(ApiError.Network)
    var updatedTitle: Pair<String, String?>? = null
    val pendingLikes = ArrayDeque<CompletableDeferred<ApiResult<PostLikeResponse>>>()
    var likeResult: ApiResult<PostLikeResponse> = ApiResult.Success(
        PostLikeResponse(
            postId = "photo-1",
            likeCount = 25,
            isLiked = true,
        ),
    )

    override suspend fun getTodayPostStatus(): ApiResult<TodayPostResponse> = todayPostResult

    override suspend fun getPostCalendar(month: YearMonth): ApiResult<PostCalendarResponse> = calendarResult

    override suspend fun getPostCalendarMonths(): ApiResult<PostCalendarMonthsResponse> =
        ApiResult.Success(PostCalendarMonthsResponse(months = emptyList()))

    override suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse> = detailResult

    override suspend fun deletePost(postId: String): ApiResult<Unit> = deleteResult

    override suspend fun updatePostTitle(
        postId: String,
        title: String?,
    ): ApiResult<PostTitleUpdateResponse> {
        updatedTitle = postId to title
        return titleUpdateResult
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse> {
        postsQueries += query
        return postsResult
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse> = if (pendingLikes.isEmpty()) likeResult else pendingLikes.removeFirst().await()
}

private class FakeHomeTopicRemoteDataSource : TopicRemoteDataSource {
    var result: ApiResult<TopicResponse> = ApiResult.Success(
        TopicResponse(
            id = "topic-id",
            title = "바다",
            topicDate = "2026-08-28",
        ),
    )

    override fun getCachedTopic(date: LocalDate): TopicResponse? = null

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> = result
}

private fun calendarResponse(
    month: Int = 8,
    posts: List<PostCalendarItemResponse> = listOf(calendarPost()),
) = PostCalendarResponse(
    year = 2026,
    month = month,
    posts = posts,
)

private fun calendarPost(
    day: Int = 31,
    status: String = "APPROVED",
) = PostCalendarItemResponse(
    topicDate = "2026-08-${day.toString().padStart(2, '0')}",
    postId = "post-$day",
    thumbnailImageUrl = "https://example.com/post-$day-thumbnail.jpg",
    status = status,
)

private fun postPage(
    posts: List<PostResponse> = listOf(
        homePost(
            id = "photo-1",
            title = "바다",
            submittedAt = "2026-08-28T01:00:00Z",
            thumbnailImageUrl = "photo-thumbnail",
            signatureThumbnailImageUrl = "signature-thumbnail",
            isLiked = true,
            isMine = true,
        ),
        homePost(
            id = "photo-2",
            title = " ",
            originalImageUrl = "photo-original-2",
            thumbnailImageUrl = "photo-thumbnail-2",
            signatureOriginalImageUrl = "signature-original-2",
        ),
        homePost(id = "photo-3", title = null),
    ),
    hasNext: Boolean = posts.isNotEmpty(),
    randomSeed: String? = "seed-1",
) = PostPageResponse(
    currentPage = 1,
    pageSize = 20,
    hasNext = hasNext,
    randomSeed = randomSeed,
    posts = posts,
)

private fun homePost(
    id: String,
    title: String?,
    originalImageUrl: String = "photo-original",
    thumbnailImageUrl: String = "photo-thumbnail",
    signatureOriginalImageUrl: String = "signature-original",
    signatureThumbnailImageUrl: String = "signature-thumbnail",
    submittedAt: String? = null,
    isLiked: Boolean = false,
    isMine: Boolean = false,
    likeCount: Long = 24,
) = PostResponse(
    id = id,
    authorId = "author-$id",
    originalImageUrl = originalImageUrl,
    thumbnailImageUrl = thumbnailImageUrl,
    signatureOriginalImageUrl = signatureOriginalImageUrl,
    signatureThumbnailImageUrl = signatureThumbnailImageUrl,
    submittedAt = submittedAt,
    title = title,
    likeCount = likeCount,
    isLiked = isLiked,
    isMine = isMine,
)

private fun authorPostsResponse() = AuthorPostsResponse(
    currentPage = 2,
    pageSize = 20,
    hasNext = true,
    posts = listOf(
        AuthorPostResponse(
            id = "author-photo",
            likeCount = 23,
            isLiked = true,
            originalImageUrl = "original",
            topic = TopicResponse(id = "topic", title = "주제", topicDate = "2026-10-19"),
        ),
    ),
)
