package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.remote.model.ErrorResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class ApiRequestExecutor(
    private val json: Json,
    private val onUnauthorized: suspend (accessToken: String) -> Unit,
) {
    suspend fun <T> execute(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let(ApiResult<T>::Success)
                ?: ApiResult.Failure(ApiError.InvalidResponse)
        } else {
            if (response.code() == HTTP_UNAUTHORIZED) {
                response
                    .raw()
                    .request
                    .tag(AuthorizationRequestContext::class.java)
                    ?.accessToken
                    ?.let { accessToken -> onUnauthorized(accessToken) }
            }

            val errorResponse = response
                .errorBody()
                ?.string()
                ?.let(::decodeError)
            ApiResult.Failure(
                ApiError.Http(
                    statusCode = response.code(),
                    errorCode = errorResponse?.errorCode,
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

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
