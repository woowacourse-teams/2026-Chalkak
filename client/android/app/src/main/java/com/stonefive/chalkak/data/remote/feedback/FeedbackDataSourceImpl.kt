package com.stonefive.chalkak.data.remote.feedback

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackDetailResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackListResponse
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

    override suspend fun getFeedbacks(
        page: Int,
        pageSize: Int,
    ): ApiResult<FeedbackListResponse> = requestExecutor.execute {
        api.getFeedbacks(page = page, pageSize = pageSize)
    }

    override suspend fun getFeedbackDetail(feedbackId: String): ApiResult<FeedbackDetailResponse> =
        requestExecutor.execute { api.getFeedbackDetail(feedbackId) }

    override suspend fun deleteFeedback(feedbackId: String): ApiResult<Unit> =
        requestExecutor.executeNoContent { api.deleteFeedback(feedbackId) }
}
