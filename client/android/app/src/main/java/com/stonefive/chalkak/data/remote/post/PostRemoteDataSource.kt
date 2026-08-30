package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.domain.model.HomeQuery

interface PostRemoteDataSource {
    suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse>

    suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse>
}
