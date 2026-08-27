package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.model.request.SignatureUploadRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialLoginRequest
import com.stonefive.chalkak.data.remote.auth.model.request.SocialSignUpRequest
import com.stonefive.chalkak.data.remote.auth.model.response.ErrorResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.auth.model.response.SocialSignUpResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class AuthDataSourceImpl(
    private val api: AuthApi,
    private val json: Json,
) : AuthDataSource {
    override suspend fun socialLogin(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SocialLoginResponse> = request {
        api.socialLogin(
            SocialLoginRequest(
                provider = provider.name,
                idToken = idToken,
            ),
        )
    }

    override suspend fun createSignatureUpload(
        provider: SocialLoginProvider,
        idToken: String,
    ): ApiResult<SignatureUploadResponse> = request {
        api.createSignatureUpload(
            SignatureUploadRequest(
                provider = provider.name,
                idToken = idToken,
            ),
        )
    }

    override suspend fun socialSignUp(signupToken: String): ApiResult<SocialSignUpResponse> = request {
        api.socialSignUp(
            SocialSignUpRequest(signupToken = signupToken),
        )
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
}
