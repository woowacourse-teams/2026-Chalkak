package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AuthorPostsPage
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostUpdate
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface PostRepository {
    val likeUpdates: Flow<PostLikeUpdate> get() = emptyFlow()

    suspend fun getAuthorPosts(
        authorId: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): HomeResult<AuthorPostsPage>

    suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar>

    suspend fun getPostCalendarMonths(): HomeResult<List<YearMonth>> = HomeResult.Success(emptyList())

    /** 그날의 주제 제목(TOPIC-005: 오늘·과거 날짜만 조회 가능). 받지 못하면 null이다. */
    suspend fun getTopicTitle(date: LocalDate): String? = null

    suspend fun getPostDetail(postId: String): HomeResult<PostDetail>

    suspend fun deletePost(postId: String): HomeResult<Unit>

    /** 제목·설명·인스타그램 동의를 통째로 교체한다(POST-010). 고치지 않은 값도 현재 값을 다시 보내야 지워지지 않는다. */
    suspend fun updatePost(
        postId: String,
        title: String?,
        description: String?,
        instagramConsent: InstagramConsent,
    ): HomeResult<PostUpdate>

    suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent>

    suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike>
}
