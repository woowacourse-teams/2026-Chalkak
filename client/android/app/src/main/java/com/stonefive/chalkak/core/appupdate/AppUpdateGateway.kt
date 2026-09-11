package com.stonefive.chalkak.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

interface AppUpdateGateway {
    suspend fun checkForUpdate(): AppUpdateCheckResult

    fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean
}

sealed interface AppUpdateCheckResult {
    data object NoUpdate : AppUpdateCheckResult

    data object ImmediateUpdateRequired : AppUpdateCheckResult

    data object Failed : AppUpdateCheckResult
}

internal fun isForcedUpdatePriority(priority: Int): Boolean = priority >= FORCE_UPDATE_MIN_PRIORITY

private const val FORCE_UPDATE_MIN_PRIORITY = 1
