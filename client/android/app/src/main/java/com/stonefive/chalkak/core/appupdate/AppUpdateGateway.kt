package com.stonefive.chalkak.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

interface AppUpdateGateway {
    val storeUrl: String? get() = null

    suspend fun checkForUpdate(): AppUpdateCheckResult

    fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean
}

sealed interface AppUpdateCheckResult {
    data object NoUpdate : AppUpdateCheckResult

    data object ImmediateUpdateRequired : AppUpdateCheckResult

    data object Failed : AppUpdateCheckResult
}
