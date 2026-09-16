package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository

class FeedbackRepositoryImpl(private val feedbackDataSource: FeedbackDataSource) : FeedbackRepository {
    override suspend fun submitFeedback(content: String) {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty() || normalizedContent.length > MAX_CONTENT_LENGTH) {
            throw FeedbackSubmissionException(FeedbackSubmissionFailure.INVALID_CONTENT)
        }

        when (val result = feedbackDataSource.submitFeedback(normalizedContent)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> throw FeedbackSubmissionException(result.error.toSubmissionFailure())
        }
    }

    private fun ApiError.toSubmissionFailure(): FeedbackSubmissionFailure = when (this) {
        ApiError.Network -> FeedbackSubmissionFailure.NETWORK

        ApiError.InvalidResponse -> FeedbackSubmissionFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            400 -> FeedbackSubmissionFailure.INVALID_CONTENT
            401 -> FeedbackSubmissionFailure.UNAUTHORIZED
            else -> FeedbackSubmissionFailure.UNKNOWN
        }
    }

    private companion object {
        const val MAX_CONTENT_LENGTH = 1_000
    }
}
