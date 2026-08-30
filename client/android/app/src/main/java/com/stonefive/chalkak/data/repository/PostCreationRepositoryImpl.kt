package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.post.PostImageEncodeResult
import com.stonefive.chalkak.data.post.PostImageEncoder
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostCreationRemoteDataSource
import com.stonefive.chalkak.data.remote.post.PostImageUploadResult
import com.stonefive.chalkak.data.remote.post.PostImageUploader
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSource
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import java.io.File
import java.time.LocalDate
import okhttp3.MediaType.Companion.toMediaType

class PostCreationRepositoryImpl(
    private val remoteDataSource: PostCreationRemoteDataSource,
    private val topicRemoteDataSource: TopicRemoteDataSource,
    private val imageEncoder: PostImageEncoder,
    private val imageUploader: PostImageUploader,
) : PostCreationRepository {
    override suspend fun getCreationTopic(topicDate: LocalDate): PostCreationTopicResult {
        val topic = when (val result = topicRemoteDataSource.getTopic(topicDate)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return PostCreationTopicResult.Failure(result.error.toTopicFailure())
        }
        val responseDate = runCatching { LocalDate.parse(topic.topicDate) }.getOrNull()
            ?: return PostCreationTopicResult.Failure(PostCreationFailure.InvalidResponse)
        if (topic.id.isBlank() || topic.title.isBlank() || responseDate != topicDate) {
            return PostCreationTopicResult.Failure(PostCreationFailure.InvalidResponse)
        }
        return PostCreationTopicResult.Success(
            Topic(
                id = topic.id,
                title = topic.title,
                date = responseDate,
            ),
        )
    }

    override suspend fun createPost(
        imageUri: String,
        title: String?,
        topic: Topic,
    ): PostCreationResult {
        if (topic.id.isBlank() || topic.title.isBlank()) {
            return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
        }

        val upload = when (val result = remoteDataSource.createPostImageUpload()) {
            is ApiResult.Success -> result.value

            is ApiResult.Failure -> {
                return PostCreationResult.Failure(result.error.toUploadPolicyFailure())
            }
        }
        if (!upload.isUsable()) {
            return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
        }

        var encodedFile: File? = null
        return try {
            val file = when (val result = imageEncoder.encode(imageUri, upload.maxBytes)) {
                is PostImageEncodeResult.Success -> result.file
                else -> return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)
            }
            encodedFile = file
            if (!file.isFile || file.length() !in 1..upload.maxBytes) {
                return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)
            }

            when (
                imageUploader.upload(
                    uploadUrl = upload.uploadUrl,
                    contentType = upload.contentType,
                    imageFile = file,
                )
            ) {
                PostImageUploadResult.Success -> Unit

                PostImageUploadResult.NetworkFailure -> {
                    return PostCreationResult.Failure(PostCreationFailure.NetworkUnavailable)
                }

                PostImageUploadResult.InvalidUploadRequest -> {
                    return PostCreationResult.Failure(PostCreationFailure.UploadRejected)
                }

                PostImageUploadResult.Rejected -> {
                    return PostCreationResult.Failure(PostCreationFailure.UploadRejected)
                }
            }

            val post = when (
                val result = remoteDataSource.createPost(
                    topicId = topic.id,
                    photoUploadId = upload.uploadId,
                    title = title.normalizedTitle(),
                )
            ) {
                is ApiResult.Success -> result.value

                is ApiResult.Failure -> {
                    return PostCreationResult.Failure(result.error.toPostCreationFailure())
                }
            }
            val moderationStatus = post.moderationStatus.toModerationStatus()
                ?: return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
            if (post.postId.isBlank()) {
                return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
            }

            PostCreationResult.Success(
                PostCreation(
                    postId = post.postId,
                    topic = topic,
                    moderationStatus = moderationStatus,
                ),
            )
        } finally {
            encodedFile?.delete()
        }
    }

    private fun String?.normalizedTitle(): String? = takeIf { !it.isNullOrBlank() }

    private fun PostImageUploadResponse.isUsable(): Boolean {
        if (uploadId.isBlank() || uploadUrl.isBlank() || contentType.isBlank() || maxBytes <= 0) {
            return false
        }
        val mediaType = runCatching { contentType.toMediaType() }.getOrNull()
            ?: return false
        return mediaType.type.equals("image", ignoreCase = true) &&
            mediaType.subtype.equals("webp", ignoreCase = true)
    }

    private fun String.toModerationStatus(): PostModerationStatus? = when (this) {
        "VALIDATING" -> PostModerationStatus.VALIDATING
        "PENDING" -> PostModerationStatus.PENDING
        else -> null
    }

    private fun ApiError.toTopicFailure(): PostCreationFailure = when (this) {
        ApiError.Network -> PostCreationFailure.NetworkUnavailable

        ApiError.InvalidResponse -> PostCreationFailure.InvalidResponse

        is ApiError.Http -> if (statusCode == HTTP_UNAUTHORIZED) {
            PostCreationFailure.ReauthenticationRequired
        } else {
            PostCreationFailure.PostCreationRejected
        }
    }

    private fun ApiError.toUploadPolicyFailure(): PostCreationFailure = when (this) {
        ApiError.Network -> PostCreationFailure.NetworkUnavailable

        ApiError.InvalidResponse -> PostCreationFailure.InvalidResponse

        is ApiError.Http -> if (statusCode == HTTP_UNAUTHORIZED) {
            PostCreationFailure.ReauthenticationRequired
        } else {
            PostCreationFailure.UploadRejected
        }
    }

    private fun ApiError.toPostCreationFailure(): PostCreationFailure = when (this) {
        ApiError.Network -> PostCreationFailure.NetworkUnavailable

        ApiError.InvalidResponse -> PostCreationFailure.InvalidResponse

        is ApiError.Http -> when {
            statusCode == HTTP_UNAUTHORIZED -> PostCreationFailure.ReauthenticationRequired
            message == ALREADY_SUBMITTED_MESSAGE -> PostCreationFailure.AlreadySubmitted
            message == TOPIC_NOT_OPEN_MESSAGE -> PostCreationFailure.TopicNotOpen
            else -> PostCreationFailure.PostCreationRejected
        }
    }

    private companion object {
        const val ALREADY_SUBMITTED_MESSAGE = "이미 해당 주제에 게시물을 작성했습니다."
        const val HTTP_UNAUTHORIZED = 401
        const val TOPIC_NOT_OPEN_MESSAGE = "현재 게시물을 작성할 수 없는 주제입니다."
    }
}
