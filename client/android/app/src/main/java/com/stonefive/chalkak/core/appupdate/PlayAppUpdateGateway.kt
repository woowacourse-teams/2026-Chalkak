package com.stonefive.chalkak.core.appupdate

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PlayAppUpdateGateway(context: Context) : AppUpdateGateway {
    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)
    private var pendingAppUpdateInfo: AppUpdateInfo? = null

    override suspend fun checkForUpdate(): AppUpdateCheckResult = suspendCancellableCoroutine { continuation ->
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (!continuation.isActive) return@addOnSuccessListener

            val result = runCatching { appUpdateInfo.toCheckResult() }
                .getOrDefault(AppUpdateCheckResult.Failed)
            pendingAppUpdateInfo = appUpdateInfo.takeIf {
                result == AppUpdateCheckResult.ImmediateUpdateRequired
            }
            continuation.resume(result)
        }
        appUpdateInfoTask.addOnFailureListener {
            if (continuation.isActive) {
                pendingAppUpdateInfo = null
                continuation.resume(AppUpdateCheckResult.Failed)
            }
        }
    }

    override fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val appUpdateInfo = pendingAppUpdateInfo ?: return false
        pendingAppUpdateInfo = null

        return runCatching {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activityResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }.getOrDefault(false)
    }

    private fun AppUpdateInfo.toCheckResult(): AppUpdateCheckResult = when (updateAvailability()) {
        UpdateAvailability.UPDATE_NOT_AVAILABLE -> AppUpdateCheckResult.NoUpdate

        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> immediateUpdateResult()

        UpdateAvailability.UPDATE_AVAILABLE -> if (isForcedUpdatePriority(updatePriority())) {
            immediateUpdateResult()
        } else {
            AppUpdateCheckResult.NoUpdate
        }

        else -> AppUpdateCheckResult.Failed
    }

    private fun AppUpdateInfo.immediateUpdateResult(): AppUpdateCheckResult =
        if (isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateCheckResult.ImmediateUpdateRequired
        } else {
            AppUpdateCheckResult.Failed
        }
}
