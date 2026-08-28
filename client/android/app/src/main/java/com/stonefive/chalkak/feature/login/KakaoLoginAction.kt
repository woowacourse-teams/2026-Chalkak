package com.stonefive.chalkak.feature.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.auth.KakaoCredentialFailure
import com.stonefive.chalkak.core.auth.KakaoCredentialResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun rememberKakaoLoginAction(
    onStart: () -> Boolean,
    onSuccess: (String) -> Unit,
    onCancelled: () -> Unit,
    onFailure: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findKakaoLoginActivity() }
    val kakaoIdTokenClient = remember(context) {
        (context.applicationContext as ChalkakApplication).appContainer.kakaoIdTokenClient
    }
    val coroutineScope = rememberCoroutineScope()
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val currentOnFailure by rememberUpdatedState(onFailure)

    return remember(activity, kakaoIdTokenClient, coroutineScope) {
        {
            val currentActivity = activity
            if (currentActivity == null) {
                currentOnFailure("카카오 로그인을 시작할 수 없어요.")
            } else if (currentOnStart()) {
                coroutineScope.launch {
                    try {
                        when (val result = kakaoIdTokenClient.getIdToken(currentActivity)) {
                            is KakaoCredentialResult.Success -> currentOnSuccess(result.idToken)

                            KakaoCredentialResult.Cancelled -> currentOnCancelled()

                            is KakaoCredentialResult.Failure -> {
                                currentOnFailure(result.reason.toUserMessage())
                            }
                        }
                    } catch (error: CancellationException) {
                        currentOnCancelled()
                        throw error
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findKakaoLoginActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findKakaoLoginActivity()
    else -> null
}

internal fun KakaoCredentialFailure.toUserMessage(): String = when (this) {
    KakaoCredentialFailure.CONFIGURATION ->
        "카카오 로그인 설정을 확인해 주세요."

    KakaoCredentialFailure.LOGIN_FAILED,
    KakaoCredentialFailure.UNKNOWN,
    -> "카카오 로그인에 실패했어요. 다시 시도해 주세요."
}
