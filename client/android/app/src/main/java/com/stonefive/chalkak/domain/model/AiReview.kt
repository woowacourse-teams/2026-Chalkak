package com.stonefive.chalkak.domain.model

data class AiReview(
    val status: Status,
    val requestedAt: String?,
    val completedAt: String?,
    val result: AiReviewContent?,
    val canRequestAgain: Boolean,
) {
    enum class Status {
        NONE,
        PROCESSING,
        COMPLETED,
        FAILED,
    }
}

data class AiReviewContent(
    val overallScore: Double,
    val criteria: List<AiReviewCriterion>,
    val distinctivePerspective: Boolean,
    val feedback: AiReviewFeedback,
)

data class AiReviewCriterion(
    val key: String,
    val label: String,
    val score: Double,
)

data class AiReviewFeedback(
    val summary: String,
    val strength: String,
    val weakness: String,
    val suggestion: String,
)
