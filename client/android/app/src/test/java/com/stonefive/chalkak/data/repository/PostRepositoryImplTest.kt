package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSource
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostResponse
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSource
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostSort
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
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
    fun `게시물 상세 응답을 Feed 도메인 모델로 변환한다`() = runTest {
        remoteDataSource.detailResult = ApiResult.Success(
            PostDetailResponse(
                id = "photo-1",
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
            ),
        )

        val result = repository.getPostDetail("photo-1") as HomeResult.Success<PostDetail>

        assertEquals(
            PostDetail(
                post = Post(
                    id = "photo-1",
                    originalImageUrl = "photo-original",
                    thumbnailImageUrl = "photo-thumbnail",
                    signatureOriginalImageUrl = "signature-original",
                    signatureThumbnailImageUrl = null,
                    contentDescription = "작품 이미지: 상세 제목",
                    title = "상세 제목",
                    likeCount = 42,
                    isLiked = true,
                ),
                topic = "새 바다",
                topicDate = LocalDate.of(2026, 8, 29),
            ),
            result.value,
        )
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
        assertEquals("photo-original-2", content.photos[1].originalImageUrl)
        assertEquals("photo-thumbnail-2", content.photos[1].thumbnailImageUrl)
        assertEquals("signature-original-2", content.photos[1].signatureOriginalImageUrl)
        assertEquals("signature-thumbnail", content.photos[1].signatureThumbnailImageUrl)
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
    val postsQueries = mutableListOf<HomeQuery>()
    var detailResult: ApiResult<PostDetailResponse> = ApiResult.Failure(ApiError.Network)
    var postsResult: ApiResult<PostPageResponse> = ApiResult.Success(postPage())
    var likeResult: ApiResult<PostLikeResponse> = ApiResult.Success(
        PostLikeResponse(
            postId = "photo-1",
            likeCount = 25,
            isLiked = true,
        ),
    )

    override suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse> = detailResult

    override suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse> {
        postsQueries += query
        return postsResult
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse> = likeResult
}

private class FakeHomeTopicRemoteDataSource : TopicRemoteDataSource {
    var result: ApiResult<TopicResponse> = ApiResult.Success(
        TopicResponse(
            id = "topic-id",
            title = "바다",
            topicDate = "2026-08-28",
        ),
    )

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> = result
}

private fun postPage(
    posts: List<PostResponse> = listOf(
        homePost(
            id = "photo-1",
            title = "바다",
            submittedAt = "2026-08-28T01:00:00Z",
            thumbnailImageUrl = "photo-thumbnail",
            signatureThumbnailImageUrl = "signature-thumbnail",
            isLiked = true,
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
    likeCount: Long = 24,
) = PostResponse(
    id = id,
    originalImageUrl = originalImageUrl,
    thumbnailImageUrl = thumbnailImageUrl,
    signatureOriginalImageUrl = signatureOriginalImageUrl,
    signatureThumbnailImageUrl = signatureThumbnailImageUrl,
    submittedAt = submittedAt,
    title = title,
    likeCount = likeCount,
    isLiked = isLiked,
)
