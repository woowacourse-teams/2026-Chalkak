package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.aireview.AiReviewDataSource
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewCriterionResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewFeedbackResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResultResponse
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewCriterion
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult

class AiReviewRepositoryImpl(private val dataSource: AiReviewDataSource) : AiReviewRepository {
    override suspend fun getReview(postId: String): AiReviewResponseResult =
        dataSource.getReview(postId).toResponseResult()

    override suspend fun requestReview(postId: String): AiReviewResponseResult =
        dataSource.requestReview(postId).toResponseResult()

    private fun ApiResult<AiReviewResponse>.toResponseResult() = when (this) {
        is ApiResult.Success -> value.toDomain()?.let(AiReviewResponseResult::Success)
            ?: AiReviewResponseResult.Failure(AiReviewFailure.InvalidResponse)

        is ApiResult.Failure -> AiReviewResponseResult.Failure(error.toDomain())
    }

    private fun AiReviewResponse.toDomain(): AiReview? = runCatching {
        val reviewStatus = AiReview.Status.valueOf(status)
        val reviewResult = when (reviewStatus) {
            AiReview.Status.COMPLETED -> requireNotNull(result).toContent()

            AiReview.Status.NONE,
            AiReview.Status.PROCESSING,
            AiReview.Status.FAILED,
            -> require(result == null) { "Non-completed review must not have a result" }.let { null }
        }
        AiReview(
            status = reviewStatus,
            requestedAt = requestedAt,
            completedAt = completedAt,
            result = reviewResult,
            canRequestAgain = canRequestAgain,
        )
    }.getOrNull()

    private fun AiReviewResultResponse.toContent() = AiReviewContent(
        overallScore = overallScore,
        criteria = criteria.map { it.toCriterion() },
        distinctivePerspective = distinctivePerspective,
        feedback = feedback.toFeedback(),
    )

    private fun AiReviewCriterionResponse.toCriterion() = AiReviewCriterion(key, label, score)

    private fun AiReviewFeedbackResponse.toFeedback() = AiReviewFeedback(
        summary,
        strength,
        weakness,
        suggestion,
    )

    private fun ApiError.toDomain(): AiReviewFailure = when (this) {
        ApiError.Network -> AiReviewFailure.Network
        ApiError.InvalidResponse -> AiReviewFailure.InvalidResponse
        is ApiError.Http -> AiReviewFailure.Http(statusCode, errorCode, message)
    }
}
