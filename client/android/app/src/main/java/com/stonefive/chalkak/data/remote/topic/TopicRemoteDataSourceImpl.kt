package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate

class TopicRemoteDataSourceImpl(
    private val topicApi: TopicApi,
    private val requestExecutor: ApiRequestExecutor,
) : TopicRemoteDataSource {
    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> = requestExecutor.execute {
        topicApi.getTopic(date.toString())
    }
}
