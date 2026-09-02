package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate

interface TopicRemoteDataSource {
    fun getCachedTopic(date: LocalDate): TopicResponse?

    suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse>
}
