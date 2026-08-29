package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostCreateResponse
import com.stonefive.chalkak.data.remote.post.model.PostImageUploadResponse
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate

interface PostRemoteDataSource {
    suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse>

    suspend fun createPostImageUpload(): ApiResult<PostImageUploadResponse>

    suspend fun createPost(
        topicId: String,
        photoUploadId: String,
        title: String?,
    ): ApiResult<PostCreateResponse>
}
