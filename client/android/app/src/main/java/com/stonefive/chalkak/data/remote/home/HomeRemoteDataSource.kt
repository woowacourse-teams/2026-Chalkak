package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostPageResponse
import com.stonefive.chalkak.data.remote.home.model.HomeTopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import java.time.LocalDate

interface HomeRemoteDataSource {
    suspend fun getTopic(date: LocalDate): ApiResult<HomeTopicResponse>

    suspend fun getPosts(query: HomeQuery): ApiResult<HomePostPageResponse>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<HomeLikeResponse>
}
