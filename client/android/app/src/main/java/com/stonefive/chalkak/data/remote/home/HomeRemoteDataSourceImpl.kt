package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomePostPageResponse
import com.stonefive.chalkak.data.remote.home.model.HomeTopicResponse
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.PostSort
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class HomeRemoteDataSourceImpl(
    private val api: HomeApi,
    private val json: Json,
) : HomeRemoteDataSource {
    override suspend fun getTopic(date: LocalDate): ApiResult<HomeTopicResponse> = request {
        api.getTopic(date.toString())
    }

    override suspend fun getPosts(query: HomeQuery): ApiResult<HomePostPageResponse> = request {
        api.getPosts(
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
    ): ApiResult<HomeLikeResponse> = request {
        if (isLiked) api.likePost(photoId) else api.unlikePost(photoId)
    }

    private suspend fun <T> request(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let(ApiResult<T>::Success)
                ?: ApiResult.Failure(ApiError.InvalidResponse)
        } else {
            val errorCode = response
                .errorBody()
                ?.string()
                ?.let(::decodeErrorCode)
            ApiResult.Failure(
                ApiError.Http(
                    statusCode = response.code(),
                    errorCode = errorCode,
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

    private fun decodeErrorCode(body: String): String? = runCatching {
        json.decodeFromString<HomeErrorResponse>(body).errorCode
    }.getOrNull()
}

private val PostSort.apiValue: String
    get() = when (this) {
        PostSort.LATEST -> "recent"
        PostSort.POPULAR -> "popular"
        PostSort.RANDOM -> "random"
    }

@Serializable
private data class HomeErrorResponse(val errorCode: String? = null)
