package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.AuthorPostsResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarMonthsResponse
import com.stonefive.chalkak.data.remote.post.model.PostCalendarResponse
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.post.model.PostUpdateResponse
import com.stonefive.chalkak.data.remote.post.model.TodayPostResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import java.time.YearMonth

interface PostRemoteDataSource {
    suspend fun getAuthorPosts(
        authorId: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<AuthorPostsResponse>

    suspend fun getTodayPostStatus(): ApiResult<TodayPostResponse>

    suspend fun getPostCalendar(month: YearMonth): ApiResult<PostCalendarResponse>

    suspend fun getPostCalendarMonths(): ApiResult<PostCalendarMonthsResponse>

    suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse>

    suspend fun deletePost(postId: String): ApiResult<Unit>

    suspend fun updatePost(
        postId: String,
        title: String?,
        description: String?,
        instagramUploadAgreed: Boolean,
        instagramId: String?,
    ): ApiResult<PostUpdateResponse>

    suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse>
}
