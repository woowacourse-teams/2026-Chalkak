package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class PostCreation(
    val postId: String,
    val topicId: String,
    val topic: String,
    val topicDate: LocalDate,
    val moderationStatus: PostModerationStatus,
)

data class PostCreationTopic(
    val id: String,
    val title: String,
    val date: LocalDate,
)

sealed interface PostCreationTopicResult {
    data class Success(val value: PostCreationTopic) : PostCreationTopicResult

    data class Failure(val reason: PostCreationFailure) : PostCreationTopicResult
}

enum class PostModerationStatus {
    VALIDATING,
    PENDING,
}

sealed interface PostCreationResult {
    data class Success(val value: PostCreation) : PostCreationResult

    data class Failure(val reason: PostCreationFailure) : PostCreationResult
}

sealed interface PostCreationFailure {
    data object ReauthenticationRequired : PostCreationFailure

    data object NetworkUnavailable : PostCreationFailure

    data object ImagePreparationFailed : PostCreationFailure

    data object UploadRejected : PostCreationFailure

    data object PostCreationRejected : PostCreationFailure

    data object AlreadySubmitted : PostCreationFailure

    data object TopicNotOpen : PostCreationFailure

    data object InvalidResponse : PostCreationFailure

    companion object {
        val Unauthorized: PostCreationFailure
            get() = ReauthenticationRequired
    }
}
