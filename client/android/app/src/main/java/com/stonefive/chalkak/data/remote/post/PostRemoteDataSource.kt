package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostCalendarResponse
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import java.time.LocalDate
import java.time.YearMonth

interface PostRemoteDataSource {
    suspend fun getPostCalendar(month: YearMonth): ApiResult<PostCalendarResponse>

    suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse>

    suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse>

    suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse>
}
