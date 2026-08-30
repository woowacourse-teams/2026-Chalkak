package com.stonefive.chalkak.domain.model

data class PostCreation(
    val postId: String,
    val topic: Topic,
    val moderationStatus: PostModerationStatus,
)

sealed interface PostCreationTopicResult {
    data class Success(val value: Topic) : PostCreationTopicResult

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
}
