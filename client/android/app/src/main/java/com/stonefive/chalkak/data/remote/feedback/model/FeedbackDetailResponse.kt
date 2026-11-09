package com.stonefive.chalkak.data.remote.feedback.model

import kotlinx.serialization.Serializable

/** `GET feedbacks/{feedbackId}` 응답. 미답변이거나 답변이 삭제됐으면 `answerContent`·`answeredAt`이 둘 다 null이다. */
@Serializable
data class FeedbackDetailResponse(
    val feedbackId: String,
    val content: String,
    val createdAt: String,
    val answerContent: String? = null,
    val answeredAt: String? = null,
)
