package com.stonefive.chalkak.data.remote.feedback.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackSubmissionRequest(val content: String)
