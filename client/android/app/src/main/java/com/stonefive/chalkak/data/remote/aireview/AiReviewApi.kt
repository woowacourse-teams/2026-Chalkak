package com.stonefive.chalkak.data.remote.aireview

import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Read-only lookups. Safe to retry, so they run on the default backend client.
interface AiReviewQueryApi {
    @GET("posts/{postId}/ai-review")
    suspend fun getReview(@Path("postId") postId: String): Response<AiReviewResponse>
}

// Review requests. Must stay on the client that never retries them transparently.
interface AiReviewRequestApi {
    @POST("posts/{postId}/ai-review")
    suspend fun requestReview(@Path("postId") postId: String): Response<AiReviewResponse>
}
