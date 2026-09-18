package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse

interface FeedbackDataSource {
    suspend fun submitFeedback(content: String): ApiResult<FeedbackSubmissionResponse>
}
