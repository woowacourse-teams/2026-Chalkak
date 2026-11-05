package com.stonefive.chalkak.data.remote.feedback.model

import kotlinx.serialization.Serializable

/** `GET feedbacks?page=&pageSize=` 응답. 항목에는 답변 정보가 없다. */
@Serializable
data class FeedbackListResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val feedbacks: List<FeedbackSummaryResponse>,
)

@Serializable
data class FeedbackSummaryResponse(
    val feedbackId: String,
    val content: String,
    val createdAt: String,
)
