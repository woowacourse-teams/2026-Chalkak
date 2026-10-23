package com.stonefive.chalkak.data.remote.aireview.model

import kotlinx.serialization.Serializable

@Serializable
data class AiReviewResponse(
    val status: String,
    val requestedAt: String? = null,
    val completedAt: String? = null,
    val result: AiReviewResultResponse? = null,
    val canRequestAgain: Boolean = false,
)

@Serializable
data class AiReviewResultResponse(
    val overallScore: Double,
    val criteria: List<AiReviewCriterionResponse>,
    val distinctivePerspective: Boolean,
    val feedback: AiReviewFeedbackResponse,
)

@Serializable
data class AiReviewCriterionResponse(
    val key: String,
    val label: String,
    val score: Double,
)

@Serializable
data class AiReviewFeedbackResponse(
    val summary: String,
    val strength: String,
    val weakness: String,
    val suggestion: String,
)
