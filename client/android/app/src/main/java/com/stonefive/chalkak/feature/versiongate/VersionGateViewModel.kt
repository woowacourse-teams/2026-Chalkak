package com.stonefive.chalkak.feature.versiongate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.appupdate.AppUpdateCheckResult
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.network.ConnectivityObserver
import com.stonefive.chalkak.core.network.ConnectivityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VersionGateViewModel(
    private val appUpdateGateway: AppUpdateGateway,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VersionGateUiState())
    val uiState: StateFlow<VersionGateUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var isOnline = false
    private var hasObservedConnectivity = false
    private var isImmediateUpdateFlowActive = false

    init {
        viewModelScope.launch {
            connectivityObserver.status.collect { status ->
                val nextIsOnline = status == ConnectivityStatus.Online
                val hasConnectivityChanged = !hasObservedConnectivity || isOnline != nextIsOnline
                isOnline = nextIsOnline
                hasObservedConnectivity = true

                if (!hasConnectivityChanged) return@collect

                if (isOnline) {
                    checkForUpdate()
                } else {
                    handleOffline()
                }
            }
        }
    }

    fun retry() {
        if (!isOnline) {
            handleOffline()
            return
        }

        checkForUpdate()
    }

    fun onImmediateUpdateStarted() {
        isImmediateUpdateFlowActive = true
        updateStatus(VersionGateStatus.UpdateInProgress)
    }

    fun onImmediateUpdateFinished() {
        isImmediateUpdateFlowActive = false
        if (isOnline) {
            checkForUpdate(force = true)
        } else {
            handleOffline()
        }
    }

    fun onResume() {
        if (_uiState.value.status == VersionGateStatus.UpdateInProgress) {
            isImmediateUpdateFlowActive = false
            if (isOnline) {
                checkForUpdate(force = true)
            } else {
                handleOffline()
            }
        }
    }

    private fun checkForUpdate(force: Boolean = false) {
        if (!isOnline || checkJob?.isActive == true) return
        if (!force && _uiState.value.status == VersionGateStatus.UpdateInProgress) return

        checkJob = viewModelScope.launch {
            updateStatus(VersionGateStatus.Checking)

            val result = try {
                appUpdateGateway.checkForUpdate()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AppUpdateCheckResult.Failed
            }

            if (!isActive) return@launch

            val status = when (result) {
                AppUpdateCheckResult.NoUpdate -> VersionGateStatus.Accessible
                AppUpdateCheckResult.ImmediateUpdateRequired -> VersionGateStatus.UpdateRequired
                AppUpdateCheckResult.Failed -> VersionGateStatus.CheckFailed
            }
            updateStatus(status)
        }
    }

    private fun handleOffline() {
        checkJob?.cancel()
        if (!isImmediateUpdateFlowActive && _uiState.value.status != VersionGateStatus.UpdateRequired) {
            updateStatus(VersionGateStatus.Accessible)
        }
    }

    private fun updateStatus(status: VersionGateStatus) {
        _uiState.update { currentState ->
            currentState.copy(
                status = status,
                hasPassedVersionGate = currentState.hasPassedVersionGate || status == VersionGateStatus.Accessible,
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                VersionGateViewModel(
                    appUpdateGateway = application.appContainer.appUpdateGateway,
                    connectivityObserver = application.appContainer.connectivityObserver,
                )
            }
        }
    }
}

data class VersionGateUiState(
    val status: VersionGateStatus = VersionGateStatus.Checking,
    val hasPassedVersionGate: Boolean = false,
)

sealed interface VersionGateStatus {
    data object Checking : VersionGateStatus

    data object Accessible : VersionGateStatus

    data object UpdateRequired : VersionGateStatus

    data object UpdateInProgress : VersionGateStatus

    data object CheckFailed : VersionGateStatus
}
