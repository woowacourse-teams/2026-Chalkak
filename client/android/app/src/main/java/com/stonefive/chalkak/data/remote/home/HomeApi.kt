package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostPageResponse
import com.stonefive.chalkak.data.remote.home.model.HomeTopicResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface HomeApi {
    @GET("topics")
    suspend fun getTopic(@Query("date") date: String): Response<HomeTopicResponse>

    @GET("posts")
    suspend fun getPosts(
        @Query("topicDate") topicDate: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("randomSeed") randomSeed: String? = null,
    ): Response<HomePostPageResponse>

    @PUT("posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: String): Response<HomeLikeResponse>

    @DELETE("posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: String): Response<HomeLikeResponse>
}
