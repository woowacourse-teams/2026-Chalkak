package com.stonefive.chalkak.feature.aireview

import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent

data class AiReviewUiState(
    val phase: AiReviewPhase = AiReviewPhase.Hidden,
    val result: AiReviewContent? = null,
    val message: String? = null,
    val status: AiReview.Status? = null,
)

enum class AiReviewPhase {
    Hidden,
    Ready,
    Processing,
    Completed,
}
