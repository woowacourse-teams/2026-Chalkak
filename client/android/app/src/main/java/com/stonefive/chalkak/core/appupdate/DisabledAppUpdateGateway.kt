package com.stonefive.chalkak.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

class DisabledAppUpdateGateway : AppUpdateGateway {
    override suspend fun checkForUpdate(): AppUpdateCheckResult = AppUpdateCheckResult.NoUpdate

    override fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        false
}
