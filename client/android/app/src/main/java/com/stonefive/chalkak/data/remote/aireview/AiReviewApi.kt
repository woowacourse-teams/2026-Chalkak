package com.stonefive.chalkak.data.remote.aireview

import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiReviewApi {
    @GET("posts/{postId}/ai-review")
    suspend fun getReview(@Path("postId") postId: String): Response<AiReviewResponse>

    @POST("posts/{postId}/ai-review")
    suspend fun requestReview(@Path("postId") postId: String): Response<AiReviewResponse>
}
