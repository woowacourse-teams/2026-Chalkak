package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PostApi {
    @GET("posts")
    suspend fun getPosts(
        @Query("topicDate") topicDate: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("randomSeed") randomSeed: String? = null,
    ): Response<PostPageResponse>

    @PUT("posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: String): Response<PostLikeResponse>

    @DELETE("posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: String): Response<PostLikeResponse>
}
