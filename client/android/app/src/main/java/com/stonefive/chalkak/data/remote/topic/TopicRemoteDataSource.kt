package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate

interface TopicRemoteDataSource {
    suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse>
}
