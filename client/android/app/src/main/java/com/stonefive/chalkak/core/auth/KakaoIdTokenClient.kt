package com.stonefive.chalkak.core.auth

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

private typealias KakaoLoginCallback = (OAuthToken?, Throwable?) -> Unit

class KakaoIdTokenClient(private val loginGateway: KakaoLoginGateway = UserApiClientKakaoLoginGateway()) {
    suspend fun getIdToken(context: Context): KakaoCredentialResult = getIdToken(
        isKakaoTalkLoginAvailable = { loginGateway.isKakaoTalkLoginAvailable(context) },
        loginWithKakaoTalk = { rawNonce -> loginGateway.loginWithKakaoTalk(context, rawNonce) },
        loginWithKakaoAccount = { rawNonce -> loginGateway.loginWithKakaoAccount(context, rawNonce) },
    )
}

suspend fun getIdToken(
    isKakaoTalkLoginAvailable: () -> Boolean,
    loginWithKakaoTalk: suspend (rawNonce: String) -> KakaoCredentialResult,
    loginWithKakaoAccount: suspend (rawNonce: String) -> KakaoCredentialResult,
    rawNonce: String = SocialLoginNonce.generate(),
): KakaoCredentialResult = try {
    if (!isKakaoTalkLoginAvailable()) {
        loginWithKakaoAccount(rawNonce)
    } else {
        when (val result = loginWithKakaoTalk(rawNonce)) {
            is KakaoCredentialResult.Success,
            KakaoCredentialResult.Cancelled,
            -> result

            is KakaoCredentialResult.Failure -> {
                if (result.reason == KakaoCredentialFailure.CONFIGURATION) {
                    result
                } else {
                    loginWithKakaoAccount(rawNonce)
                }
            }
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    KakaoCredentialResult.Failure(KakaoCredentialFailure.UNKNOWN)
}

interface KakaoLoginGateway {
    fun isKakaoTalkLoginAvailable(context: Context): Boolean

    suspend fun loginWithKakaoTalk(
        context: Context,
        rawNonce: String,
    ): KakaoCredentialResult

    suspend fun loginWithKakaoAccount(
        context: Context,
        rawNonce: String,
    ): KakaoCredentialResult
}

private class UserApiClientKakaoLoginGateway(private val userApiClient: UserApiClient = UserApiClient.instance) :
    KakaoLoginGateway {
    override fun isKakaoTalkLoginAvailable(context: Context): Boolean = userApiClient.isKakaoTalkLoginAvailable(context)

    override suspend fun loginWithKakaoTalk(
        context: Context,
        rawNonce: String,
    ): KakaoCredentialResult = loginWithKakao(rawNonce) { callback ->
        userApiClient.loginWithKakaoTalk(
            context,
            nonce = SocialLoginNonce.sha256Hex(rawNonce),
            callback = callback,
        )
    }

    override suspend fun loginWithKakaoAccount(
        context: Context,
        rawNonce: String,
    ): KakaoCredentialResult = loginWithKakao(rawNonce) { callback ->
        userApiClient.loginWithKakaoAccount(
            context,
            nonce = SocialLoginNonce.sha256Hex(rawNonce),
            callback = callback,
        )
    }

    private suspend fun loginWithKakao(
        rawNonce: String,
        startLogin: (KakaoLoginCallback) -> Unit,
    ): KakaoCredentialResult = suspendCancellableCoroutine { continuation ->
        try {
            startLogin { token, error ->
                if (continuation.isActive) {
                    continuation.resume(token.toKakaoCredentialResult(error, rawNonce))
                }
            }
        } catch (error: CancellationException) {
            continuation.cancel(error)
        } catch (error: Exception) {
            continuation.resume(
                KakaoCredentialResult.Failure(KakaoCredentialFailure.LOGIN_FAILED),
            )
        }
    }

    private fun OAuthToken?.toKakaoCredentialResult(
        error: Throwable?,
        rawNonce: String,
    ): KakaoCredentialResult {
        if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
            return KakaoCredentialResult.Cancelled
        }
        if (error != null) {
            return KakaoCredentialResult.Failure(KakaoCredentialFailure.LOGIN_FAILED)
        }

        val idToken = this?.idToken
        return if (idToken.isNullOrBlank()) {
            KakaoCredentialResult.Failure(KakaoCredentialFailure.CONFIGURATION)
        } else {
            KakaoCredentialResult.Success(idToken, rawNonce)
        }
    }
}

sealed interface KakaoCredentialResult {
    data class Success(
        val idToken: String,
        val rawNonce: String,
    ) : KakaoCredentialResult

    data object Cancelled : KakaoCredentialResult

    data class Failure(val reason: KakaoCredentialFailure) : KakaoCredentialResult
}

enum class KakaoCredentialFailure {
    LOGIN_FAILED,
    CONFIGURATION,
    UNKNOWN,
}
