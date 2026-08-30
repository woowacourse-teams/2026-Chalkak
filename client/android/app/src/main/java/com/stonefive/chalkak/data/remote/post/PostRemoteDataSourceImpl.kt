package com.stonefive.chalkak.data.remote.post

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.model.ErrorResponse
import com.stonefive.chalkak.data.remote.post.model.PostDetailResponse
import com.stonefive.chalkak.data.remote.post.model.PostLikeResponse
import com.stonefive.chalkak.data.remote.post.model.PostPageResponse
import com.stonefive.chalkak.data.remote.topic.TopicApi
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class PostRemoteDataSourceImpl(
    private val topicApi: TopicApi,
    private val postApi: PostApi,
    private val json: Json,
) : PostRemoteDataSource {
    override suspend fun getPostDetail(postId: String): ApiResult<PostDetailResponse> = request {
        postApi.getPost(postId)
    }

    override suspend fun getTopic(date: LocalDate): ApiResult<TopicResponse> = request {
        topicApi.getTopic(date.toString())
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<PostPageResponse> = request {
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
    ): ApiResult<PostLikeResponse> = request {
        if (isLiked) postApi.likePost(photoId) else postApi.unlikePost(photoId)
    }

    private suspend fun <T> request(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let(ApiResult<T>::Success)
                ?: ApiResult.Failure(ApiError.InvalidResponse)
        } else {
            val errorResponse = response
                .errorBody()
                ?.string()
                ?.let(::decodeError)
                ?: return ApiResult.Failure(ApiError.InvalidResponse)
            ApiResult.Failure(
                ApiError.Http(
                    statusCode = response.code(),
                    errorCode = errorResponse.errorCode,
                    message = errorResponse.message,
                ),
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        ApiResult.Failure(ApiError.Network)
    } catch (_: SerializationException) {
        ApiResult.Failure(ApiError.InvalidResponse)
    }

    private fun decodeError(body: String): ErrorResponse? =
        runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull()
}

private val PostSort.apiValue: String
    get() = when (this) {
        PostSort.LATEST -> "recent"
        PostSort.POPULAR -> "popular"
        PostSort.RANDOM -> "random"
    }
