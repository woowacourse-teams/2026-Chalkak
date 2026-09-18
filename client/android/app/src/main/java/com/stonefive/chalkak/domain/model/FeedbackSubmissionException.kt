package com.stonefive.chalkak.domain.model

enum class FeedbackSubmissionFailure {
    INVALID_CONTENT,
    UNAUTHORIZED,
    NETWORK,
    UNKNOWN,
}

class FeedbackSubmissionException(val reason: FeedbackSubmissionFailure) : Exception()
