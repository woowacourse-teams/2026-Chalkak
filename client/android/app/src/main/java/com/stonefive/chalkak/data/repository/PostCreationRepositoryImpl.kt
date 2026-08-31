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
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostImagePreparation
import com.stonefive.chalkak.domain.model.PostImagePreparationResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

class PostCreationRepositoryImpl(
    private val remoteDataSource: PostCreationRemoteDataSource,
    private val topicRemoteDataSource: TopicRemoteDataSource,
    private val imageEncoder: PostImageEncoder,
    private val imageUploader: PostImageUploader,
    private val nanoTime: () -> Long = System::nanoTime,
) : PostCreationRepository {
    private val preparedImages = mutableMapOf<String, PreparedImage>()
    private val preparedImagesLock = Any()

    override fun getCachedCreationTopic(topicDate: LocalDate): Topic? =
        topicRemoteDataSource.getCachedTopic(topicDate)?.toTopicOrNull(topicDate)

    override suspend fun getCreationTopic(topicDate: LocalDate): PostCreationTopicResult {
        val response = when (val result = topicRemoteDataSource.getTopic(topicDate)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return PostCreationTopicResult.Failure(result.error.toTopicFailure())
        }
        val topic = response.toTopicOrNull(topicDate)
            ?: return PostCreationTopicResult.Failure(PostCreationFailure.InvalidResponse)
        return PostCreationTopicResult.Success(topic)
    }

    private fun TopicResponse.toTopicOrNull(requestedDate: LocalDate): Topic? {
        val responseDate = runCatching { LocalDate.parse(topicDate) }.getOrNull() ?: return null
        if (id.isBlank() || title.isBlank() || responseDate != requestedDate) return null
        return Topic(id = id, title = title, date = responseDate)
    }

    override suspend fun prepareImage(imageUri: String): PostImagePreparationResult {
        val policyRequestStartedAtNanos = nanoTime()
        val upload = when (val result = issueUploadPolicy()) {
            is UploadPolicyResult.Success -> result.value
            is UploadPolicyResult.Failure -> return PostImagePreparationResult.Failure(result.reason)
        }

        var file: File? = null
        var retained = false
        return try {
            file = when (val result = imageEncoder.encode(imageUri, upload.maxBytes)) {
                is PostImageEncodeResult.Success -> result.file
                else -> return PostImagePreparationResult.Failure(PostCreationFailure.ImagePreparationFailed)
            }
            if (!file.isFile || file.length() !in 1..upload.maxBytes) {
                return PostImagePreparationResult.Failure(PostCreationFailure.ImagePreparationFailed)
            }

            val preparation = PostImagePreparation(UUID.randomUUID().toString())
            val preparedImage = PreparedImage(
                sourceUri = imageUri,
                file = file,
                upload = upload,
                uploadUrlExpiresAtNanos = uploadUrlExpiryNanos(
                    requestStartedAtNanos = policyRequestStartedAtNanos,
                    expiresInSeconds = upload.expiresInSeconds,
                ),
            )
            synchronized(preparedImagesLock) {
                preparedImages[preparation.id] = preparedImage
            }
            retained = true
            PostImagePreparationResult.Success(preparation)
        } finally {
            if (!retained) file?.delete()
        }
    }

    override fun discardPreparedImage(preparation: PostImagePreparation) {
        val preparedImage = synchronized(preparedImagesLock) {
            preparedImages.remove(preparation.id)
        }
        preparedImage?.file?.delete()
    }

    override suspend fun createPost(
        preparation: PostImagePreparation,
        title: String?,
        topic: Topic,
    ): PostCreationResult {
        if (topic.id.isBlank() || topic.title.isBlank()) {
            discardPreparedImage(preparation)
            return PostCreationResult.Failure(PostCreationFailure.InvalidResponse)
        }

        val preparedImage = synchronized(preparedImagesLock) {
            preparedImages.remove(preparation.id)
        } ?: return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)

        var upload = preparedImage.upload
        var uploadFile = preparedImage.file
        return try {
            if (nanoTime() >= preparedImage.uploadUrlExpiresAtNanos) {
                upload = when (val result = issueUploadPolicy()) {
                    is UploadPolicyResult.Success -> result.value
                    is UploadPolicyResult.Failure -> return PostCreationResult.Failure(result.reason)
                }
                if (uploadFile.length() !in 1..upload.maxBytes) {
                    val replacement = when (
                        val result = imageEncoder.encode(
                            preparedImage.sourceUri,
                            upload.maxBytes,
                        )
                    ) {
                        is PostImageEncodeResult.Success -> result.file
                        else -> return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)
                    }
                    if (!replacement.isFile || replacement.length() !in 1..upload.maxBytes) {
                        replacement.delete()
                        return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)
                    }
                    uploadFile.delete()
                    uploadFile = replacement
                }
            }

            if (!uploadFile.isFile || uploadFile.length() !in 1..upload.maxBytes) {
                return PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed)
            }

            when (
                imageUploader.upload(
                    uploadUrl = upload.uploadUrl,
                    contentType = upload.contentType,
                    imageFile = uploadFile,
                )
            ) {
                PostImageUploadResult.Success -> Unit

                PostImageUploadResult.NetworkFailure -> {
                    return PostCreationResult.Failure(PostCreationFailure.NetworkUnavailable)
                }

                PostImageUploadResult.InvalidUploadRequest,
                PostImageUploadResult.Rejected,
                -> return PostCreationResult.Failure(PostCreationFailure.UploadRejected)
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
            uploadFile.delete()
            if (uploadFile != preparedImage.file) preparedImage.file.delete()
        }
    }

    private suspend fun issueUploadPolicy(): UploadPolicyResult {
        val upload = when (val result = remoteDataSource.createPostImageUpload()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return UploadPolicyResult.Failure(result.error.toUploadPolicyFailure())
        }
        if (!upload.isUsable()) {
            return UploadPolicyResult.Failure(PostCreationFailure.InvalidResponse)
        }
        return UploadPolicyResult.Success(upload)
    }

    private fun uploadUrlExpiryNanos(
        requestStartedAtNanos: Long,
        expiresInSeconds: Long,
    ): Long {
        val usableSeconds = (expiresInSeconds - EXPIRY_SAFETY_SECONDS).coerceAtLeast(0)
        val duration = if (usableSeconds > Long.MAX_VALUE / NANOS_PER_SECOND) {
            Long.MAX_VALUE
        } else {
            TimeUnit.SECONDS.toNanos(usableSeconds)
        }
        return if (
            duration == Long.MAX_VALUE ||
            requestStartedAtNanos > Long.MAX_VALUE - duration
        ) {
            Long.MAX_VALUE
        } else {
            requestStartedAtNanos + duration
        }
    }

    private fun String?.normalizedTitle(): String? = takeIf { !it.isNullOrBlank() }

    private fun PostImageUploadResponse.isUsable(): Boolean {
        if (
            uploadId.isBlank() ||
            uploadUrl.isBlank() ||
            expiresInSeconds <= 0 ||
            contentType.isBlank() ||
            maxBytes <= 0
        ) {
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

    private data class PreparedImage(
        val sourceUri: String,
        val file: File,
        val upload: PostImageUploadResponse,
        val uploadUrlExpiresAtNanos: Long,
    )

    private sealed interface UploadPolicyResult {
        data class Success(val value: PostImageUploadResponse) : UploadPolicyResult

        data class Failure(val reason: PostCreationFailure) : UploadPolicyResult
    }

    private companion object {
        const val ALREADY_SUBMITTED_MESSAGE = "이미 해당 주제에 게시물을 작성했습니다."
        const val EXPIRY_SAFETY_SECONDS = 5L
        const val HTTP_UNAUTHORIZED = 401
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val TOPIC_NOT_OPEN_MESSAGE = "현재 게시물을 작성할 수 없는 주제입니다."
    }
}
