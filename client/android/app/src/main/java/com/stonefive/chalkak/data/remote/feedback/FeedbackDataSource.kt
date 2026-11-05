package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackDetailResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackListResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse

interface FeedbackDataSource {
    suspend fun submitFeedback(content: String): ApiResult<FeedbackSubmissionResponse>

    suspend fun getFeedbacks(
        page: Int,
        pageSize: Int,
    ): ApiResult<FeedbackListResponse>

    suspend fun getFeedbackDetail(feedbackId: String): ApiResult<FeedbackDetailResponse>

    suspend fun deleteFeedback(feedbackId: String): ApiResult<Unit>
}
