package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.post.PostImageEncodeResult
import com.stonefive.chalkak.data.post.PostImageEncoder
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSource
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import com.stonefive.chalkak.data.remote.signature.PresignedImageUploader
import com.stonefive.chalkak.data.remote.signature.PresignedUploadResult
import com.stonefive.chalkak.data.remote.signature.UploadContent
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.repository.PostRepository
import java.io.File
import java.time.LocalDate
import okhttp3.MediaType.Companion.toMediaType

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val imageEncoder: PostImageEncoder,
    private val imageUploader: PresignedImageUploader,
) : PostRepository {
    override suspend fun createPost(
        imageUri: String,
        title: String?,
        topicDate: LocalDate,
    ): PostCreationResult {
        val topic = when (val result = remoteDataSource.getTopic(topicDate)) {
            is ApiResult.Success -> result.value

            is ApiResult.Failure -> {
                return PostCreationResult.Failure(result.error.toTopicFailure())
            }
        }
        val topicDate = runCatching { LocalDate.parse(topic.topicDate) }.getOrNull()
            ?: return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
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
                    content = UploadContent.FileContent(file),
                )
            ) {
                PresignedUploadResult.Success -> Unit

                PresignedUploadResult.NetworkFailure -> {
                    return PostCreationResult.Failure(PostCreationFailure.NetworkUnavailable)
                }

                PresignedUploadResult.InvalidUploadUrl -> {
                    return PostCreationResult.Failure(PostCreationFailure.UploadRejected)
                }

                PresignedUploadResult.Rejected -> {
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
                    topicId = topic.id,
                    topic = topic.title,
                    topicDate = topicDate,
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

        is ApiError.Http -> if (statusCode == HTTP_UNAUTHORIZED) {
            PostCreationFailure.ReauthenticationRequired
        } else {
            PostCreationFailure.PostCreationRejected
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
