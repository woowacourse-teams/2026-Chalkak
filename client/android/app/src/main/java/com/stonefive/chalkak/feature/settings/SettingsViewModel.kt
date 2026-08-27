package com.stonefive.chalkak.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    versionName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            versionName = versionName,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<SettingsUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        loadProfile()
    }

    fun showLogoutDialog() {
        _uiState.update { it.copy(accountDialog = SettingsAccountDialog.LOGOUT) }
    }

    fun showWithdrawDialog() {
        _uiState.update { it.copy(accountDialog = SettingsAccountDialog.WITHDRAW) }
    }

    fun dismissAccountDialog() {
        _uiState.update { it.copy(accountDialog = null) }
    }

    fun confirmAccountAction() {
        val accountDialog = _uiState.value.accountDialog ?: return
        _uiState.update { it.copy(accountDialog = null) }

        when (accountDialog) {
            SettingsAccountDialog.LOGOUT -> clearProfile(authRepository::logout)
            SettingsAccountDialog.WITHDRAW -> clearProfile(authRepository::withdraw)
        }
    }

    private fun clearProfile(block: suspend () -> Unit) {
        if (_uiState.value.isAccountActionInProgress) return

        _uiState.update { it.copy(isAccountActionInProgress = true) }

        viewModelScope.launch {
            try {
                block()
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        isAccountActionInProgress = false,
                        signatureModel = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(isAccountActionInProgress = false) }
                _uiEvent.send(SettingsUiEvent.AccountActionFailed)
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            if (authRepository.sessionState.value !is UserSessionState.Authenticated) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = false,
                        signatureModel = null,
                    )
                }
                return@launch
            }

            try {
                val profile = authRepository.getMyProfile()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        signatureModel = profile?.signatureUrl,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                SettingsViewModel(
                    authRepository = application.appContainer.authRepository,
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
