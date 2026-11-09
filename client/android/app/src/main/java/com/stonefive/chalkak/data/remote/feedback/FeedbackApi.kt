package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.feedback.model.FeedbackDetailResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackListResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionRequest
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FeedbackApi {
    @POST("feedbacks")
    suspend fun submitFeedback(@Body request: FeedbackSubmissionRequest): Response<FeedbackSubmissionResponse>

    @GET("feedbacks")
    suspend fun getFeedbacks(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<FeedbackListResponse>

    @GET("feedbacks/{feedbackId}")
    suspend fun getFeedbackDetail(@Path("feedbackId") feedbackId: String): Response<FeedbackDetailResponse>

    /** 성공은 본문 없는 204다. 본문을 파싱하지 않게 `Response<Unit>`으로 받는다. */
    @DELETE("feedbacks/{feedbackId}")
    suspend fun deleteFeedback(@Path("feedbackId") feedbackId: String): Response<Unit>
}
