package com.stonefive.chalkak.data.remote.feedback.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackSubmissionResponse(
    val feedbackId: String,
    val createdAt: String,
)
