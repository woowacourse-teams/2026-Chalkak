package com.stonefive.chalkak.domain.repository

interface FeedbackRepository {
    suspend fun submitFeedback(content: String)
}
