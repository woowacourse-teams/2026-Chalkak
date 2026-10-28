package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AiReview

interface AiReviewRepository {
    suspend fun getReview(postId: String): AiReviewResponseResult

    suspend fun requestReview(postId: String): AiReviewResponseResult
}

sealed interface AiReviewResponseResult {
    data class Success(val review: AiReview) : AiReviewResponseResult

    data class Failure(val error: AiReviewFailure) : AiReviewResponseResult
}

sealed interface AiReviewFailure {
    data object Network : AiReviewFailure

    data object InvalidResponse : AiReviewFailure

    data class Http(
        val statusCode: Int,
        val errorCode: String?,
        val message: String?,
    ) : AiReviewFailure
}
