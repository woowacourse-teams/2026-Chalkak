package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class TopicRemoteDataSourceImpl(
    private val topicApi: TopicApi,
    private val requestExecutor: ApiRequestExecutor,
) : TopicRemoteDataSource {
    private val cachedTopics = ConcurrentHashMap<LocalDate, TopicResponse>()

    override fun getCachedTopic(date: LocalDate): TopicResponse? = cachedTopics[date]

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> {
        val result = requestExecutor.execute {
            topicApi.getTopic(date.toString())
        }
        if (result is ApiResult.Success) cachedTopics[date] = result.value
        return result
    }
}
