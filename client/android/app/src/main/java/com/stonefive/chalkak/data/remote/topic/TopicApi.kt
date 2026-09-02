package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TopicApi {
    @GET("topics")
    suspend fun getTopic(@Query("date") date: String): Response<TopicResponse>
}
