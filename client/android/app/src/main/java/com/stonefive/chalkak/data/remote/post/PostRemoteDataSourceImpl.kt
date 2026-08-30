package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

class PostRemoteDataSourceImpl(
    private val postApi: PostApi,
    private val requestExecutor: ApiRequestExecutor,
) : PostRemoteDataSource {
    override suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse> = requestExecutor.execute {
        postApi.getPost(postId)
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse> = requestExecutor.execute {
        postApi.getPosts(
            topicDate = query.date.toString(),
            sort = query.sort.apiValue,
            page = query.page,
            pageSize = query.pageSize,
            randomSeed = query.randomSeed.takeIf { query.sort == PostSort.RANDOM },
        )
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): ApiResult<PostLikeResponse> = requestExecutor.execute {
        if (isLiked) postApi.likePost(photoId) else postApi.unlikePost(photoId)
    }
}

private val PostSort.apiValue: String
    get() = when (this) {
        PostSort.LATEST -> "recent"
        PostSort.POPULAR -> "popular"
        PostSort.RANDOM -> "random"
    }
