package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.post.PostImageEncodeResult
import com.stonefive.chalkak.data.post.PostImageEncoder
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.PostCreationRemoteDataSource
import com.stonefive.chalkak.data.remote.post.PostImageUploadResult
import com.stonefive.chalkak.data.remote.post.PostImageUploader
import com.stonefive.chalkak.data.remote.post.model.PostCreateResponse
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
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCreationRepositoryImplTest {
    private val events = mutableListOf<String>()
    private val remote = FakePostCreationRemoteDataSource(events)
    private val topicRemote = FakeCreationTopicRemoteDataSource(events)
    private val encoder = FakePostImageEncoder(events)
    private val uploader = FakePostImageUploader(events)
    private val requestedDate = LocalDate.of(2026, 8, 29)
    private val topic = Topic("topic-id", "바다", requestedDate)
    private var nowNanos = 0L
    private val repository = PostCreationRepositoryImpl(
        remoteDataSource = remote,
        topicRemoteDataSource = topicRemote,
        imageEncoder = encoder,
        imageUploader = uploader,
        nanoTime = { nowNanos },
    )

    @Test
    fun `사진 선택 시 정책과 인코딩만 실행하고 제출 시 PUT과 게시물을 생성한다`() = runTest {
        val topicResult = repository.getCreationTopic(requestedDate)
        val preparation = prepare()

        assertEquals(listOf("topic:$requestedDate", "upload-policy", "encode:content://photo/1:5242880"), events)
        assertTrue(encoder.lastFile?.exists() == true)
        assertTrue(uploader.calls.isEmpty())
        assertTrue(remote.createdTitles.isEmpty())

        val result = repository.createPost(preparation, "   ", topic)

        assertEquals(PostCreationTopicResult.Success(topic), topicResult)
        assertEquals(
            PostCreationResult.Success(
                PostCreation(
                    postId = "post-id",
                    topic = topic,
                    moderationStatus = PostModerationStatus.VALIDATING,
                ),
            ),
            result,
        )
        assertEquals(
            listOf(
                "topic:$requestedDate",
                "upload-policy",
                "encode:content://photo/1:5242880",
                "put:image/webp",
                "create:topic-id:upload-id:null",
            ),
            events,
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), uploader.uploadedBytes)
        assertFalse(encoder.lastFile?.exists() == true)
    }

    @Test
    fun `주제 조회 실패 시 이미지 흐름과 무관하게 실패를 반환한다`() = runTest {
        topicRemote.result = ApiResult.Failure(ApiError.Network)

        assertEquals(
            PostCreationTopicResult.Failure(PostCreationFailure.NetworkUnavailable),
            repository.getCreationTopic(requestedDate),
        )
        assertEquals(listOf("topic:$requestedDate"), topicRemote.calls)
        assertTrue(encoder.calls.isEmpty())
    }

    @Test
    fun `업로드 정책 실패 시 인코더를 호출하지 않는다`() = runTest {
        remote.uploadPolicyResult = ApiResult.Failure(ApiError.Http(400, "BUSINESS_ERROR"))

        assertEquals(
            PostImagePreparationResult.Failure(PostCreationFailure.UploadRejected),
            repository.prepareImage("content://photo/1"),
        )
        assertEquals(listOf("upload-policy"), remote.calls)
        assertTrue(encoder.calls.isEmpty())
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `인코딩 실패 시 준비가 실패하고 PUT을 호출하지 않는다`() = runTest {
        encoder.result = PostImageEncodeResult.DecodeFailed

        assertEquals(
            PostImagePreparationResult.Failure(PostCreationFailure.ImagePreparationFailed),
            repository.prepareImage("content://photo/1"),
        )
        assertEquals(listOf("encode:content://photo/1:5242880"), encoder.calls)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `준비 결과를 폐기하면 임시 파일을 삭제한다`() = runTest {
        val preparation = prepare()
        val file = encoder.lastFile

        repository.discardPreparedImage(preparation)

        assertFalse(file?.exists() == true)
        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.ImagePreparationFailed),
            repository.createPost(preparation, "제목", topic),
        )
    }

    @Test
    fun `PUT 실패 시 게시물 생성을 호출하지 않고 임시 파일을 삭제한다`() = runTest {
        uploader.result = PostImageUploadResult.Rejected
        val preparation = prepare()

        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.UploadRejected),
            repository.createPost(preparation, "제목", topic),
        )
        assertTrue(remote.createdTitles.isEmpty())
        assertFalse(encoder.lastFile?.exists() == true)
    }

    @Test
    fun `게시물 생성 401은 재인증 실패로 매핑하고 파일을 삭제한다`() = runTest {
        remote.createResult = ApiResult.Failure(ApiError.Http(401, "UNAUTHORIZED"))
        val preparation = prepare()

        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.ReauthenticationRequired),
            repository.createPost(preparation, "제목", topic),
        )
        assertFalse(encoder.lastFile?.exists() == true)
    }

    @Test
    fun `이미 작성한 주제 응답은 중복 제출 실패로 매핑한다`() = runTest {
        remote.createResult = ApiResult.Failure(
            ApiError.Http(
                statusCode = 400,
                errorCode = "BUSINESS_ERROR",
                message = "이미 해당 주제에 게시물을 작성했습니다.",
            ),
        )

        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.AlreadySubmitted),
            repository.createPost(prepare(), "제목", topic),
        )
    }

    @Test
    fun `닫힌 주제 응답은 참여 기간 종료 실패로 매핑한다`() = runTest {
        remote.createResult = ApiResult.Failure(
            ApiError.Http(
                statusCode = 400,
                errorCode = "BUSINESS_ERROR",
                message = "현재 게시물을 작성할 수 없는 주제입니다.",
            ),
        )

        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.TopicNotOpen),
            repository.createPost(prepare(), "제목", topic),
        )
    }

    @Test
    fun `VALIDATING과 PENDING은 모두 생성 성공으로 처리한다`() = runTest {
        listOf("VALIDATING", "PENDING").forEach { status ->
            remote.createResult = ApiResult.Success(PostCreateResponse("post-$status", status))

            val result = repository.createPost(prepare(), "제목", topic)

            assertTrue(result is PostCreationResult.Success)
            assertEquals(
                PostModerationStatus.valueOf(status),
                (result as PostCreationResult.Success).value.moderationStatus,
            )
        }
    }

    @Test
    fun `알 수 없는 moderation status는 InvalidResponse이고 파일을 삭제한다`() = runTest {
        remote.createResult = ApiResult.Success(PostCreateResponse("post-id", "APPROVED"))
        val preparation = prepare()

        assertEquals(
            PostCreationResult.Failure(PostCreationFailure.InvalidResponse),
            repository.createPost(preparation, "제목", topic),
        )
        assertFalse(encoder.lastFile?.exists() == true)
    }

    @Test
    fun `파일이 maxBytes를 넘으면 준비에 실패하고 파일을 삭제한다`() = runTest {
        encoder.fileBytes = ByteArray(5_242_881)

        assertEquals(
            PostImagePreparationResult.Failure(PostCreationFailure.ImagePreparationFailed),
            repository.prepareImage("content://photo/1"),
        )
        assertTrue(uploader.calls.isEmpty())
        assertFalse(encoder.lastFile?.exists() == true)
    }

    @Test
    fun `WebP가 아닌 Content-Type 정책은 인코딩 전에 거절한다`() = runTest {
        remote.uploadPolicyResult = ApiResult.Success(uploadPolicy(contentType = "image/png"))

        assertEquals(
            PostImagePreparationResult.Failure(PostCreationFailure.InvalidResponse),
            repository.prepareImage("content://photo/1"),
        )
        assertTrue(encoder.calls.isEmpty())
    }

    @Test
    fun `만료된 PUT URL은 새 정책을 발급하고 호환되는 준비 파일을 재사용한다`() = runTest {
        val preparation = prepare()
        remote.uploadPolicyResults += ApiResult.Success(uploadPolicy(uploadId = "upload-id-2"))
        nowNanos = TimeUnit.SECONDS.toNanos(295)

        val result = repository.createPost(preparation, "제목", topic)

        assertTrue(result is PostCreationResult.Success)
        assertEquals(2, remote.calls.count { it == "upload-policy" })
        assertEquals(1, encoder.calls.size)
        assertTrue(remote.calls.contains("create:topic-id:upload-id-2:제목"))
    }

    @Test
    fun `새 정책 제한이 더 작으면 원본 URI를 다시 인코딩한다`() = runTest {
        encoder.fileByteResults += byteArrayOf(1, 2, 3)
        encoder.fileByteResults += byteArrayOf(1, 2)
        val preparation = prepare()
        remote.uploadPolicyResults += ApiResult.Success(
            uploadPolicy(uploadId = "upload-id-2", maxBytes = 2),
        )
        nowNanos = TimeUnit.SECONDS.toNanos(295)

        val result = repository.createPost(preparation, "제목", topic)

        assertTrue(result is PostCreationResult.Success)
        assertEquals(
            listOf("encode:content://photo/1:5242880", "encode:content://photo/1:2"),
            encoder.calls,
        )
        assertArrayEquals(byteArrayOf(1, 2), uploader.uploadedBytes)
    }

    @Test
    fun `PUT 중 취소되어도 임시 파일을 삭제한다`() = runTest {
        uploader.await = CompletableDeferred()
        val preparation = prepare()
        val job = launch {
            repository.createPost(preparation, "제목", topic)
        }

        while (uploader.calls.isEmpty()) testScheduler.runCurrent()
        job.cancelAndJoin()

        assertFalse(encoder.lastFile?.exists() == true)
    }

    private suspend fun prepare(imageUri: String = "content://photo/1"): PostImagePreparation {
        val result = repository.prepareImage(imageUri)
        assertTrue(result is PostImagePreparationResult.Success)
        return (result as PostImagePreparationResult.Success).value
    }

    private fun uploadPolicy(
        uploadId: String = "upload-id",
        contentType: String = "image/webp",
        maxBytes: Long = 5_242_880,
    ) = PostImageUploadResponse(
        uploadId = uploadId,
        uploadUrl = "https://example.com/upload/$uploadId",
        expiresInSeconds = 300,
        contentType = contentType,
        maxBytes = maxBytes,
    )
}

private class FakePostCreationRemoteDataSource(private val events: MutableList<String>) : PostCreationRemoteDataSource {
    val calls = mutableListOf<String>()
    val createdTitles = mutableListOf<String?>()
    var uploadPolicyResult: ApiResult<PostImageUploadResponse> = ApiResult.Success(defaultUploadPolicy())
    val uploadPolicyResults = ArrayDeque<ApiResult<PostImageUploadResponse>>()
    var createResult: ApiResult<PostCreateResponse> = ApiResult.Success(PostCreateResponse("post-id", "VALIDATING"))

    override suspend fun createPostImageUpload(): ApiResult<PostImageUploadResponse> {
        calls += "upload-policy"
        events += "upload-policy"
        return if (uploadPolicyResults.isEmpty()) uploadPolicyResult else uploadPolicyResults.removeFirst()
    }

    override suspend fun createPost(
        topicId: String,
        photoUploadId: String,
        title: String?,
    ): ApiResult<PostCreateResponse> {
        calls += "create:$topicId:$photoUploadId:$title"
        events += "create:$topicId:$photoUploadId:$title"
        createdTitles += title
        return createResult
    }

    private companion object {
        fun defaultUploadPolicy() = PostImageUploadResponse(
            uploadId = "upload-id",
            uploadUrl = "https://example.com/upload",
            expiresInSeconds = 300,
            contentType = "image/webp",
            maxBytes = 5_242_880,
        )
    }
}

private class FakeCreationTopicRemoteDataSource(private val events: MutableList<String>) : TopicRemoteDataSource {
    val calls = mutableListOf<String>()
    var result: ApiResult<TopicResponse> = ApiResult.Success(TopicResponse("topic-id", "바다", "2026-08-29"))

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> {
        calls += "topic:$date"
        events += "topic:$date"
        return result
    }
}

private class FakePostImageEncoder(private val events: MutableList<String>) : PostImageEncoder {
    val calls = mutableListOf<String>()
    var result: PostImageEncodeResult? = null
    var fileBytes = byteArrayOf(1, 2, 3)
    val fileByteResults = ArrayDeque<ByteArray>()
    var lastFile: File? = null

    override suspend fun encode(
        contentUri: String,
        maxBytes: Long,
    ): PostImageEncodeResult {
        calls += "encode:$contentUri:$maxBytes"
        events += "encode:$contentUri:$maxBytes"
        result?.let { return it }
        val file = File.createTempFile("post-repository-test", ".webp")
        file.writeBytes(if (fileByteResults.isEmpty()) fileBytes else fileByteResults.removeFirst())
        lastFile = file
        return PostImageEncodeResult.Success(file)
    }
}

private class FakePostImageUploader(private val events: MutableList<String>) : PostImageUploader {
    val calls = mutableListOf<String>()
    var result: PostImageUploadResult = PostImageUploadResult.Success
    var uploadedBytes: ByteArray = byteArrayOf()
    var await: CompletableDeferred<Unit>? = null

    override suspend fun upload(
        uploadUrl: String,
        contentType: String,
        imageFile: File,
    ): PostImageUploadResult {
        calls += "put:$contentType"
        events += "put:$contentType"
        uploadedBytes = imageFile.readBytes()
        await?.await()
        return result
    }
}
