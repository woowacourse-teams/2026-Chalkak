package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.auth.AuthDataSource
import com.stonefive.chalkak.data.remote.auth.model.response.SocialLoginResponse
import com.stonefive.chalkak.data.remote.plusExpiresInSaturating
import com.stonefive.chalkak.data.remote.signature.SignatureUploadResult
import com.stonefive.chalkak.data.remote.signature.SignatureUploader
import com.stonefive.chalkak.domain.model.SocialAuthFailure
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.model.SocialSignUpFailure
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class AuthRepositoryImpl(
    private val authDataSource: AuthDataSource,
    private val signatureUploader: SignatureUploader,
    private val sessionStore: SessionStore,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : AuthRepository {
    private var pendingLogin: PendingSocialLogin? = null

    override val sessionState: StateFlow<UserSessionState> = sessionStore.sessionState

    override suspend fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ): SocialLoginResult = when (val result = authDataSource.socialLogin(provider, idToken)) {
        is ApiResult.Success -> handleLoginResponse(
            provider = provider,
            idToken = idToken,
            response = result.value,
        )

        is ApiResult.Failure -> SocialLoginResult.Failure(result.error.toSocialAuthFailure())
    }

    override suspend fun completeSocialSignUp(signaturePng: ByteArray): SocialSignUpResult {
        val login = pendingLogin
            ?: return SocialSignUpResult.Failure(SocialSignUpFailure.MISSING_LOGIN_CONTEXT)
        if (signaturePng.size > MAX_SIGNATURE_BYTES) {
            return SocialSignUpResult.Failure(SocialSignUpFailure.SIGNATURE_TOO_LARGE)
        }

        val upload = when (
            val result = authDataSource.createSignatureUpload(
                provider = login.provider,
                idToken = login.idToken,
            )
        ) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return SocialSignUpResult.Failure(result.error.toSignUpFailure())
        }

        when (signatureUploader.upload(upload.uploadUrl, signaturePng)) {
            SignatureUploadResult.Success -> Unit

            SignatureUploadResult.NetworkFailure -> {
                return SocialSignUpResult.Failure(SocialSignUpFailure.NETWORK_UNAVAILABLE)
            }

            SignatureUploadResult.InvalidUploadUrl -> {
                return SocialSignUpResult.Failure(SocialSignUpFailure.UNKNOWN)
            }

            SignatureUploadResult.Rejected -> {
                return SocialSignUpResult.Failure(SocialSignUpFailure.INVALID_SIGNATURE)
            }
        }

        return completeSocialSignUp(signupToken = upload.signupToken, login = login)
    }

    override suspend fun continueAsGuest() {
        pendingLogin = null
        sessionStore.continueAsGuest()
    }

    override suspend fun logout() {
        pendingLogin = null
        val refreshToken = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?.refreshToken
        if (refreshToken != null) {
            authDataSource.logout(refreshToken)
        }
        sessionStore.clear()
    }

    private suspend fun handleLoginResponse(
        provider: SocialLoginProvider,
        idToken: String,
        response: SocialLoginResponse,
    ): SocialLoginResult = when (response) {
        is SocialLoginResponse.LoginSuccess -> {
            val userId = response.userId
            pendingLogin = null
            sessionStore.saveSession(response.toSessionCredentials())
            SocialLoginResult.LoginSuccess(userId)
        }

        SocialLoginResponse.SignUpRequired -> {
            pendingLogin = PendingSocialLogin(provider, idToken)
            SocialLoginResult.SignUpRequired
        }
    }

    private suspend fun completeSocialSignUp(
        signupToken: String,
        login: PendingSocialLogin,
    ): SocialSignUpResult {
        repeat(SIGN_UP_ATTEMPTS) { attempt ->
            when (
                val result = authDataSource.socialSignUp(signupToken = signupToken)
            ) {
                is ApiResult.Success -> {
                    val response = result.value
                    pendingLogin = null
                    return authenticateAfterSignUp(response.userId, login)
                }

                is ApiResult.Failure -> {
                    if (result.error.isSignatureProcessingPending()) {
                        if (attempt == SIGN_UP_ATTEMPTS - 1) {
                            return SocialSignUpResult.Failure(
                                SocialSignUpFailure.SIGNATURE_PROCESSING_TIMEOUT,
                            )
                        }
                        retryDelay(SIGN_UP_RETRY_DELAY_MILLIS)
                    } else {
                        return SocialSignUpResult.Failure(result.error.toSignUpFailure())
                    }
                }
            }
        }
        return SocialSignUpResult.Failure(SocialSignUpFailure.UNKNOWN)
    }

    private suspend fun authenticateAfterSignUp(
        userId: String,
        login: PendingSocialLogin,
    ): SocialSignUpResult = when (
        val result = authDataSource.socialLogin(login.provider, login.idToken)
    ) {
        is ApiResult.Success -> {
            when (val response = result.value) {
                is SocialLoginResponse.LoginSuccess -> {
                    if (response.userId != userId) {
                        SocialSignUpResult.Failure(SocialSignUpFailure.REAUTHENTICATION_REQUIRED)
                    } else {
                        sessionStore.saveSession(response.toSessionCredentials())
                        SocialSignUpResult.Success(userId)
                    }
                }

                SocialLoginResponse.SignUpRequired -> {
                    SocialSignUpResult.Failure(SocialSignUpFailure.REAUTHENTICATION_REQUIRED)
                }
            }
        }

        is ApiResult.Failure -> SocialSignUpResult.Failure(result.error.toSignUpFailure())
    }

    private fun ApiError.toSocialAuthFailure(): SocialAuthFailure = when (this) {
        ApiError.Network -> SocialAuthFailure.NETWORK_UNAVAILABLE

        ApiError.InvalidResponse -> SocialAuthFailure.INVALID_RESPONSE

        is ApiError.Http -> when (statusCode) {
            401 -> SocialAuthFailure.UNAUTHORIZED
            400 -> SocialAuthFailure.UNKNOWN
            else -> SocialAuthFailure.UNKNOWN
        }
    }

    private fun ApiError.toSignUpFailure(): SocialSignUpFailure = when (this) {
        ApiError.Network -> SocialSignUpFailure.NETWORK_UNAVAILABLE

        ApiError.InvalidResponse -> SocialSignUpFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            401 -> SocialSignUpFailure.REAUTHENTICATION_REQUIRED
            404 -> SocialSignUpFailure.SIGNATURE_NOT_FOUND
            400 -> SocialSignUpFailure.INVALID_SIGNATURE
            else -> SocialSignUpFailure.UNKNOWN
        }
    }

    private fun ApiError.isSignatureProcessingPending(): Boolean = this is ApiError.Http &&
        statusCode == 400 &&
        errorCode == SIGNATURE_PROCESSING_PENDING

    private fun SocialLoginResponse.LoginSuccess.toSessionCredentials(): SessionCredentials {
        val now = currentEpochSeconds()
        return SessionCredentials(
            userId = userId,
            accessToken = accessToken,
            expiresAtEpochSeconds = now.plusExpiresInSaturating(expiresIn),
            refreshToken = refreshToken,
            refreshTokenExpiresAtEpochSeconds = now.plusExpiresInSaturating(refreshTokenExpiresIn),
        )
    }

    private data class PendingSocialLogin(
        val provider: SocialLoginProvider,
        val idToken: String,
    )

    private companion object {
        const val SIGNATURE_PROCESSING_PENDING = "SIGNATURE_PROCESSING_PENDING"
        const val MAX_SIGNATURE_BYTES = 1024 * 1024
        const val SIGN_UP_ATTEMPTS = 10
        const val SIGN_UP_RETRY_DELAY_MILLIS = 1_000L
    }
}
