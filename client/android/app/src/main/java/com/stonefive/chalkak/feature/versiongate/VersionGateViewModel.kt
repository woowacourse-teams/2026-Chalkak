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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VersionGateViewModel(
    private val appUpdateGateway: AppUpdateGateway,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<VersionGateUiState>(VersionGateUiState.Checking)
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
        _uiState.value = VersionGateUiState.UpdateInProgress
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
        if (_uiState.value == VersionGateUiState.UpdateInProgress) {
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
        if (!force && _uiState.value == VersionGateUiState.UpdateInProgress) return

        checkJob = viewModelScope.launch {
            _uiState.value = VersionGateUiState.Checking

            val result = try {
                appUpdateGateway.checkForUpdate()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AppUpdateCheckResult.Failed
            }

            if (!isActive) return@launch

            _uiState.value = when (result) {
                AppUpdateCheckResult.NoUpdate -> VersionGateUiState.Accessible
                AppUpdateCheckResult.ImmediateUpdateRequired -> VersionGateUiState.UpdateRequired
                AppUpdateCheckResult.Failed -> VersionGateUiState.CheckFailed
            }
        }
    }

    private fun handleOffline() {
        checkJob?.cancel()
        if (!isImmediateUpdateFlowActive && _uiState.value != VersionGateUiState.UpdateRequired) {
            _uiState.value = VersionGateUiState.Accessible
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

sealed interface VersionGateUiState {
    data object Checking : VersionGateUiState

    data object Accessible : VersionGateUiState

    data object UpdateRequired : VersionGateUiState

    data object UpdateInProgress : VersionGateUiState

    data object CheckFailed : VersionGateUiState
}
