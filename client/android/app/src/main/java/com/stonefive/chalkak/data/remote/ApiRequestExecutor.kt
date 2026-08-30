package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.remote.model.ErrorResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class ApiRequestExecutor(
    private val json: Json,
    private val onUnauthorized: suspend () -> Unit,
) {
    suspend fun <T> execute(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            response.body()?.let(ApiResult<T>::Success)
                ?: ApiResult.Failure(ApiError.InvalidResponse)
        } else {
            createFailure(response)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        ApiResult.Failure(ApiError.Network)
    } catch (_: SerializationException) {
        ApiResult.Failure(ApiError.InvalidResponse)
    }

    suspend fun executeNoContent(block: suspend () -> Response<*>): ApiResult<Unit> = try {
        val response = block()
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            createFailure(response)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        ApiResult.Failure(ApiError.Network)
    } catch (_: SerializationException) {
        ApiResult.Failure(ApiError.InvalidResponse)
    }

    private suspend fun createFailure(response: Response<*>): ApiResult<Nothing> {
        if (response.code() == HTTP_UNAUTHORIZED) {
            onUnauthorized()
        }

        val errorResponse = response
            .errorBody()
            ?.string()
            ?.let(::decodeError)
        return ApiResult.Failure(
            ApiError.Http(
                statusCode = response.code(),
                errorCode = errorResponse?.errorCode,
                message = errorResponse?.message,
            ),
        )
    }

    private fun decodeError(body: String): ErrorResponse? =
        runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
