package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class TodayPostStatus(
    val topicDate: LocalDate,
    val isPosted: Boolean,
    val postId: String?,
    val moderationStatus: TodayPostModerationStatus?,
)

enum class TodayPostModerationStatus {
    VALIDATING,
    PENDING,
    APPROVED,
}

sealed interface TodayPostStatusResult {
    data class Success(val value: TodayPostStatus) : TodayPostStatusResult

    data class Failure(val reason: TodayPostStatusFailure) : TodayPostStatusResult
}

sealed interface TodayPostStatusFailure {
    data object NoOpenTopic : TodayPostStatusFailure

    data object ReauthenticationRequired : TodayPostStatusFailure

    data object Suspended : TodayPostStatusFailure

    data object Network : TodayPostStatusFailure

    data object InvalidResponse : TodayPostStatusFailure

    data class Http(val statusCode: Int) : TodayPostStatusFailure
}
