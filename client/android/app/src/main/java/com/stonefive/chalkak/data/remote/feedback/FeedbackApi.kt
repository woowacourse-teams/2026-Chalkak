package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionRequest
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FeedbackApi {
    @POST("feedbacks")
    suspend fun submitFeedback(@Body request: FeedbackSubmissionRequest): Response<FeedbackSubmissionResponse>
}
