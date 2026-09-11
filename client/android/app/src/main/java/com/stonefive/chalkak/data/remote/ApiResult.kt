package com.stonefive.chalkak.data.remote

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    data object Network : ApiError

    data object InvalidResponse : ApiError

    data class Http(
        val statusCode: Int,
        val errorCode: String?,
        val message: String? = null,
    ) : ApiError
}
