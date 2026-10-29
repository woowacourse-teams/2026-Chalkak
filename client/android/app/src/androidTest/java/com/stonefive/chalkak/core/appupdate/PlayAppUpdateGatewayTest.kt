package com.stonefive.chalkak.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.stonefive.chalkak.data.remote.appversion.AppVersionApi
import com.stonefive.chalkak.data.remote.appversion.AppVersionResponse
import com.stonefive.chalkak.data.remote.appversion.ServerAppUpdateGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayAppUpdateGatewayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = FakeAppUpdateManager(context)
    private val gateway = PlayAppUpdateGateway(context, manager)

    // The Fake manager records the flow instead of launching Play UI.
    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(
            input: IntentSenderRequest,
            options: ActivityOptionsCompat?,
        ) {
            error("FakeAppUpdateManager must not launch real Play UI")
        }

        override fun unregister() = Unit

        override val contract: ActivityResultContract<IntentSenderRequest, *> =
            ActivityResultContracts.StartIntentSenderForResult()
    }

    @Test
    fun noAvailableUpdateDoesNotStartFlow() = runBlocking {
        manager.setUpdateNotAvailable()

        assertEquals(AppUpdateCheckResult.NoUpdate, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertFalse(gateway.startImmediateUpdate(launcher))
            assertFalse(manager.isImmediateFlowVisible)
        }
    }

    @Test
    fun availableUpdateStartsImmediateEvenWithZeroPlayPriority() = runBlocking {
        manager.setUpdateAvailable(28)
        manager.setUpdatePriority(0)

        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertTrue(gateway.startImmediateUpdate(launcher))
            assertTrue(manager.isImmediateFlowVisible)
            assertFalse(manager.isConfirmationDialogVisible)
            assertFalse(gateway.startImmediateUpdate(launcher))
        }
    }

    @Test
    fun flexibleOnlyUpdateDoesNotStartImmediate() = runBlocking {
        manager.setUpdateAvailable(28, AppUpdateType.FLEXIBLE)

        assertEquals(AppUpdateCheckResult.Failed, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertFalse(gateway.startImmediateUpdate(launcher))
            assertFalse(manager.isImmediateFlowVisible)
        }
    }

    @Test
    fun rejectedUpdateCanBeStartedAgainAfterFreshCheck() = runBlocking {
        manager.setUpdateAvailable(28)
        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertTrue(gateway.startImmediateUpdate(launcher))
            manager.userRejectsUpdate()
            assertFalse(manager.isImmediateFlowVisible)
        }

        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertTrue(gateway.startImmediateUpdate(launcher))
            assertTrue(manager.isImmediateFlowVisible)
        }
    }

    @Test
    fun acceptedImmediateUpdateCanResumeAndCompleteSimulatedInstall() = runBlocking {
        manager.setUpdateAvailable(28)
        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertTrue(gateway.startImmediateUpdate(launcher))
            manager.userAcceptsUpdate()
            manager.downloadStarts()
        }

        // A foreground check must still find the developer-triggered update.
        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        instrumentation.runOnMainSync {
            manager.downloadCompletes()
            assertTrue(manager.isInstallSplashScreenVisible)
            manager.installCompletes()
            assertFalse(manager.isInstallSplashScreenVisible)
        }
        assertEquals(AppUpdateCheckResult.NoUpdate, gateway.checkForUpdate())
    }

    @Test
    fun supportedVersionDoesNotOfferUpdateDespitePlayAvailability() = runBlocking {
        manager.setUpdateAvailable(28)
        val serverGateway = serverGateway(minimumVersion = "1.1.1")

        assertEquals(AppUpdateCheckResult.NoUpdate, serverGateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertFalse(serverGateway.startImmediateUpdate(launcher))
            assertFalse(manager.isImmediateFlowVisible)
        }
    }

    @Test
    fun unsupportedVersionRequiresImmediateThroughRealGateway() = runBlocking {
        manager.setUpdateAvailable(28)
        val serverGateway = serverGateway(minimumVersion = "1.1.2")

        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, serverGateway.checkForUpdate())
        instrumentation.runOnMainSync {
            assertTrue(serverGateway.startImmediateUpdate(launcher))
            assertTrue(manager.isImmediateFlowVisible)
        }
    }

    @Test
    fun unsupportedVersionRemainsRequiredWhenPlayHasNoUpdate() = runBlocking {
        manager.setUpdateNotAvailable()
        val serverGateway = serverGateway(minimumVersion = "1.1.2")

        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, serverGateway.checkForUpdate())
        assertEquals(STORE_URL, serverGateway.storeUrl)
        instrumentation.runOnMainSync {
            assertFalse(serverGateway.startImmediateUpdate(launcher))
            assertFalse(manager.isImmediateFlowVisible)
        }
    }

    private fun serverGateway(minimumVersion: String): ServerAppUpdateGateway = ServerAppUpdateGateway(
        api = object : AppVersionApi {
            override suspend fun getAppVersion(platform: String): AppVersionResponse =
                AppVersionResponse(platform, minimumVersion, STORE_URL)
        },
        currentVersion = "1.1.1",
        playGateway = gateway,
    )

    private companion object {
        const val STORE_URL = "https://play.google.com/store/apps/details?id=com.stonefive.chalkak"
    }
}
