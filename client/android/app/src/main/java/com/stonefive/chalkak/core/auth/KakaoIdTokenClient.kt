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

class KakaoIdTokenClient internal constructor(
    private val loginGateway: KakaoLoginGateway = UserApiClientKakaoLoginGateway(),
) {
    suspend fun getIdToken(context: Context): KakaoCredentialResult = getIdToken(
        isKakaoTalkLoginAvailable = { loginGateway.isKakaoTalkLoginAvailable(context) },
        loginWithKakaoTalk = { loginGateway.loginWithKakaoTalk(context) },
        loginWithKakaoAccount = { loginGateway.loginWithKakaoAccount(context) },
    )
}

internal suspend fun getIdToken(
    isKakaoTalkLoginAvailable: () -> Boolean,
    loginWithKakaoTalk: suspend () -> KakaoCredentialResult,
    loginWithKakaoAccount: suspend () -> KakaoCredentialResult,
): KakaoCredentialResult = try {
    if (!isKakaoTalkLoginAvailable()) {
        loginWithKakaoAccount()
    } else {
        when (val result = loginWithKakaoTalk()) {
            is KakaoCredentialResult.Success,
            KakaoCredentialResult.Cancelled,
            -> result

            is KakaoCredentialResult.Failure -> {
                if (result.reason == KakaoCredentialFailure.CONFIGURATION) {
                    result
                } else {
                    loginWithKakaoAccount()
                }
            }
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    KakaoCredentialResult.Failure(KakaoCredentialFailure.UNKNOWN)
}

internal interface KakaoLoginGateway {
    fun isKakaoTalkLoginAvailable(context: Context): Boolean

    suspend fun loginWithKakaoTalk(context: Context): KakaoCredentialResult

    suspend fun loginWithKakaoAccount(context: Context): KakaoCredentialResult
}

private class UserApiClientKakaoLoginGateway(private val userApiClient: UserApiClient = UserApiClient.instance) :
    KakaoLoginGateway {
    override fun isKakaoTalkLoginAvailable(context: Context): Boolean = userApiClient.isKakaoTalkLoginAvailable(context)

    override suspend fun loginWithKakaoTalk(context: Context): KakaoCredentialResult = loginWithKakao { callback ->
        userApiClient.loginWithKakaoTalk(context, callback = callback)
    }

    override suspend fun loginWithKakaoAccount(context: Context): KakaoCredentialResult = loginWithKakao { callback ->
        userApiClient.loginWithKakaoAccount(context, callback = callback)
    }

    private suspend fun loginWithKakao(startLogin: (KakaoLoginCallback) -> Unit): KakaoCredentialResult =
        suspendCancellableCoroutine { continuation ->
            try {
                startLogin { token, error ->
                    if (continuation.isActive) {
                        continuation.resume(token.toKakaoCredentialResult(error))
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

    private fun OAuthToken?.toKakaoCredentialResult(error: Throwable?): KakaoCredentialResult {
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
            KakaoCredentialResult.Success(idToken)
        }
    }
}

sealed interface KakaoCredentialResult {
    data class Success(val idToken: String) : KakaoCredentialResult

    data object Cancelled : KakaoCredentialResult

    data class Failure(val reason: KakaoCredentialFailure) : KakaoCredentialResult
}

enum class KakaoCredentialFailure {
    LOGIN_FAILED,
    CONFIGURATION,
    UNKNOWN,
}
