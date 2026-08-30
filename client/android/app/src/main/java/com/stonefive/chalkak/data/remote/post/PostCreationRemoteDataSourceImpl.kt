package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostCreateRequest
import com.stonefive.chalkak.data.remote.post.model.PostCreateResponse
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import com.stonefive.chalkak.data.remote.topic.TopicApi
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate

class PostCreationRemoteDataSourceImpl(
    private val postApi: PostApi,
    private val topicApi: TopicApi,
    private val requestExecutor: ApiRequestExecutor,
) : PostCreationRemoteDataSource {
    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> = requestExecutor.execute {
        topicApi.getTopic(date.toString())
    }

    override suspend fun createPostImageUpload(): ApiResult<PostImageUploadResponse> = requestExecutor.execute {
        postApi.createPostImageUpload()
    }

    override suspend fun createPost(
        topicId: String,
        photoUploadId: String,
        title: String?,
    ): ApiResult<PostCreateResponse> = requestExecutor.execute {
        postApi.createPost(
            PostCreateRequest(
                topicId = topicId,
                photoUploadId = photoUploadId,
                title = title,
            ),
        )
    }
}
