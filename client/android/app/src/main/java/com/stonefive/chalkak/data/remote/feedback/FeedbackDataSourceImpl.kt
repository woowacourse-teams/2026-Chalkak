package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionRequest
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse

class FeedbackDataSourceImpl(
    private val api: FeedbackApi,
    private val requestExecutor: ApiRequestExecutor,
) : FeedbackDataSource {
    override suspend fun submitFeedback(content: String): ApiResult<FeedbackSubmissionResponse> =
        requestExecutor.execute {
            api.submitFeedback(
                FeedbackSubmissionRequest(content = content),
            )
        }
}
