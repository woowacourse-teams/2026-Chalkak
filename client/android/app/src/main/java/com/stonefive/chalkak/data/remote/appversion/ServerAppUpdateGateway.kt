package com.stonefive.chalkak.data.remote.appversion

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.stonefive.chalkak.core.appupdate.AppUpdateCheckResult
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import java.net.URI
import kotlinx.coroutines.CancellationException

class ServerAppUpdateGateway(
    private val api: AppVersionApi,
    private val currentVersion: String,
    private val playGateway: AppUpdateGateway,
) : AppUpdateGateway {
    override var storeUrl: String? = null
        private set

    override suspend fun checkForUpdate(): AppUpdateCheckResult = try {
        val policy = api.getAppVersion()
        require(policy.platform == "ANDROID")
        val minimum = policy.minimumSupportedVersion
        if (minimum == null) {
            require(policy.storeUrl == null)
            storeUrl = null
            AppUpdateCheckResult.NoUpdate
        } else if (compareVersions(currentVersion, minimum) >= 0) {
            storeUrl = null
            AppUpdateCheckResult.NoUpdate
        } else {
            val updateStoreUrl = requireNotNull(policy.storeUrl)
            val storeUri = URI(updateStoreUrl)
            require(storeUri.scheme in setOf("https", "market") && !storeUri.host.isNullOrBlank())
            storeUrl = updateStoreUrl
            // Play delivery is best-effort. The server policy remains authoritative,
            // including installations outside Play and unavailable update tracks.
            try {
                playGateway.checkForUpdate()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The required-update screen can still open the server store URL.
            }
            AppUpdateCheckResult.ImmediateUpdateRequired
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AppUpdateCheckResult.Failed
    }

    override fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        playGateway.startImmediateUpdate(activityResultLauncher)
}

internal fun compareVersions(
    current: String,
    minimum: String,
): Int {
    fun components(value: String): List<Int> {
        require(Regex("[0-9]+(\\.[0-9]+){2}").matches(value))
        return value.split('.').map(String::toInt)
    }
    val left = components(current)
    val right = components(minimum)
    return left
        .zip(right)
        .firstOrNull { (a, b) -> a != b }
        ?.let { (a, b) -> a.compareTo(b) } ?: 0
}
