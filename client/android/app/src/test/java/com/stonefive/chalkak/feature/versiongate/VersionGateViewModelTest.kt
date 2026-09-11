package com.stonefive.chalkak.feature.versiongate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.appupdate.AppUpdateCheckResult
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.appupdate.isForcedUpdatePriority
import com.stonefive.chalkak.core.network.ConnectivityObserver
import com.stonefive.chalkak.core.network.ConnectivityStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VersionGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `Play 우선순위 0은 허용하고 1 이상은 강제한다`() {
        assertFalse(isForcedUpdatePriority(0))
        assertTrue(isForcedUpdatePriority(1))
        assertTrue(isForcedUpdatePriority(2))
    }

    @Test
    fun `오프라인에서는 앱 진입을 허용하고 온라인 전환 시 업데이트를 검사한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.NoUpdate)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Offline)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()

        assertEquals(VersionGateStatus.Accessible, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.hasPassedVersionGate)
        assertEquals(0, gateway.checkCount)

        connectivityObserver.mutableStatus.value = ConnectivityStatus.Online
        advanceUntilIdle()

        assertEquals(VersionGateStatus.Accessible, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.hasPassedVersionGate)
        assertEquals(1, gateway.checkCount)
    }

    @Test
    fun `앱 진입 후 업데이트가 발견되어도 통과 이력을 유지한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.ImmediateUpdateRequired)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Offline)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()

        assertEquals(VersionGateStatus.Accessible, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.hasPassedVersionGate)

        connectivityObserver.mutableStatus.value = ConnectivityStatus.Online
        advanceUntilIdle()

        assertEquals(VersionGateStatus.UpdateRequired, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.hasPassedVersionGate)
    }

    @Test
    fun `강제 업데이트가 있으면 앱 진입을 막고 업데이트 진행 상태로 전환한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.ImmediateUpdateRequired)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Online)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()

        assertEquals(VersionGateStatus.UpdateRequired, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.hasPassedVersionGate)

        viewModel.onImmediateUpdateStarted()

        assertEquals(VersionGateStatus.UpdateInProgress, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.hasPassedVersionGate)
    }

    @Test
    fun `진행 중 업데이트로 복귀하면 Play 업데이트 상태를 다시 확인한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.ImmediateUpdateRequired)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Online)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()
        viewModel.onImmediateUpdateStarted()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(VersionGateStatus.UpdateRequired, viewModel.uiState.value.status)
        assertEquals(2, gateway.checkCount)
    }

    @Test
    fun `온라인 상태에서 업데이트 확인에 실패하면 앱 진입을 막고 재시도로 회복한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.Failed)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Online)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()

        assertEquals(VersionGateStatus.CheckFailed, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.hasPassedVersionGate)

        gateway.result = AppUpdateCheckResult.NoUpdate
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(VersionGateStatus.Accessible, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.hasPassedVersionGate)
        assertEquals(2, gateway.checkCount)
    }

    @Test
    fun `업데이트 완료 후에도 업데이트가 남아 있으면 다시 강제 업데이트를 시작한다`() = runTest {
        val gateway = FakeAppUpdateGateway(AppUpdateCheckResult.ImmediateUpdateRequired)
        val connectivityObserver = FakeConnectivityObserver(ConnectivityStatus.Online)
        val viewModel = VersionGateViewModel(gateway, connectivityObserver)

        advanceUntilIdle()
        viewModel.onImmediateUpdateStarted()
        viewModel.onImmediateUpdateFinished()
        advanceUntilIdle()

        assertEquals(VersionGateStatus.UpdateRequired, viewModel.uiState.value.status)
        assertTrue(gateway.checkCount >= 2)
    }
}

private class FakeAppUpdateGateway(var result: AppUpdateCheckResult) : AppUpdateGateway {
    var checkCount = 0
        private set

    override suspend fun checkForUpdate(): AppUpdateCheckResult {
        checkCount++
        return result
    }

    override fun startImmediateUpdate(activityResultLauncher: UpdateLauncher): Boolean = true
}

private class FakeConnectivityObserver(initialStatus: ConnectivityStatus) : ConnectivityObserver {
    val mutableStatus = MutableStateFlow(initialStatus)
    override val status: StateFlow<ConnectivityStatus> = mutableStatus
}

private typealias UpdateLauncher = ActivityResultLauncher<IntentSenderRequest>
