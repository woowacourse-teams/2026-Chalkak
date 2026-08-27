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
import com.stonefive.chalkak.core.auth.GoogleCredentialFailure
import com.stonefive.chalkak.core.auth.GoogleCredentialResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun rememberGoogleLoginAction(
    onStart: () -> Boolean,
    onSuccess: (String) -> Unit,
    onCancelled: () -> Unit,
    onFailure: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val googleIdTokenClient = remember(context) {
        (context.applicationContext as ChalkakApplication).appContainer.googleIdTokenClient
    }
    val coroutineScope = rememberCoroutineScope()
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val currentOnFailure by rememberUpdatedState(onFailure)

    return remember(activity, googleIdTokenClient, coroutineScope) {
        {
            val currentActivity = activity
            if (currentActivity == null) {
                currentOnFailure("Google 로그인을 시작할 수 없어요.")
            } else if (currentOnStart()) {
                coroutineScope.launch {
                    try {
                        when (val result = googleIdTokenClient.getIdToken(currentActivity)) {
                            is GoogleCredentialResult.Success -> currentOnSuccess(result.idToken)

                            GoogleCredentialResult.Cancelled -> currentOnCancelled()

                            is GoogleCredentialResult.Failure -> {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun GoogleCredentialFailure.toUserMessage(): String = when (this) {
    GoogleCredentialFailure.NO_CREDENTIAL -> "사용 가능한 Google 계정이 없어요."

    GoogleCredentialFailure.INTERRUPTED -> "Google 로그인이 중단됐어요. 다시 시도해 주세요."

    GoogleCredentialFailure.CONFIGURATION -> "Google 로그인 설정을 확인해 주세요."

    GoogleCredentialFailure.UNSUPPORTED -> "이 기기에서는 Google 로그인을 사용할 수 없어요."

    GoogleCredentialFailure.UNEXPECTED_CREDENTIAL,
    GoogleCredentialFailure.INVALID_CREDENTIAL,
    GoogleCredentialFailure.UNKNOWN,
    -> "Google 로그인에 실패했어요. 다시 시도해 주세요."
}
