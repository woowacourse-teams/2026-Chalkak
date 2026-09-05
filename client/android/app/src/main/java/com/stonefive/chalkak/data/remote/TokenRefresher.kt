package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.remote.auth.RefreshApi
import com.stonefive.chalkak.data.remote.auth.model.request.RefreshTokenRequest
import com.stonefive.chalkak.data.remote.auth.model.response.RefreshTokenResponseDto
import com.stonefive.chalkak.data.remote.model.ErrorResponse
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface TokenRefreshResult {
    data class Success(val credentials: SessionCredentials) : TokenRefreshResult

    data object ReauthenticationRequired : TokenRefreshResult

    data object TransientFailure : TokenRefreshResult
}

interface TokenRefresher {
    suspend fun refresh(
        userId: String,
        refreshToken: String,
    ): TokenRefreshResult
}

class AuthTokenRefresher(
    private val refreshApi: RefreshApi,
    private val json: Json,
    private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : TokenRefresher {
    override suspend fun refresh(
        userId: String,
        refreshToken: String,
    ): TokenRefreshResult = try {
        val response = refreshApi.refresh(RefreshTokenRequest(refreshToken))
        if (response.isSuccessful) {
            response
                .body()
                ?.toSessionCredentials(userId)
                ?.let(TokenRefreshResult::Success)
                ?: TokenRefreshResult.TransientFailure
        } else {
            val errorCode = response
                .errorBody()
                ?.string()
                ?.let(::decodeErrorCode)
            if (errorCode == REAUTHENTICATION_REQUIRED || response.code() == HTTP_UNAUTHORIZED) {
                TokenRefreshResult.ReauthenticationRequired
            } else {
                TokenRefreshResult.TransientFailure
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        TokenRefreshResult.TransientFailure
    } catch (_: SerializationException) {
        TokenRefreshResult.TransientFailure
    }

    private fun RefreshTokenResponseDto.toSessionCredentials(userId: String): SessionCredentials? {
        val validAccessToken = accessToken?.takeIf(String::isNotBlank) ?: return null
        val validRefreshToken = refreshToken?.takeIf(String::isNotBlank) ?: return null
        val validExpiresIn = expiresIn?.takeIf { it > 0 } ?: return null
        val validRefreshTokenExpiresIn = refreshTokenExpiresIn?.takeIf { it > 0 } ?: return null
        val now = currentEpochSeconds()
        return SessionCredentials(
            userId = userId,
            accessToken = validAccessToken,
            expiresAtEpochSeconds = now.plusExpiresInSaturating(validExpiresIn),
            refreshToken = validRefreshToken,
            refreshTokenExpiresAtEpochSeconds = now.plusExpiresInSaturating(validRefreshTokenExpiresIn),
        )
    }

    private fun decodeErrorCode(body: String): String? =
        runCatching { json.decodeFromString<ErrorResponse>(body).errorCode }.getOrNull()

    private companion object {
        const val REAUTHENTICATION_REQUIRED = "REAUTHENTICATION_REQUIRED"
        const val HTTP_UNAUTHORIZED = 401
    }
}
