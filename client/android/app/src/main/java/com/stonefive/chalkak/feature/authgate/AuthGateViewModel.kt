package com.stonefive.chalkak.feature.authgate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthGateViewModel(authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthGateUiState>(AuthGateUiState.Loading)
    val uiState: StateFlow<AuthGateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState
                .collect { sessionState ->
                    _uiState.value = when (sessionState) {
                        UserSessionState.Loading -> AuthGateUiState.Loading

                        UserSessionState.SignedOut -> AuthGateUiState.LoginRequired

                        UserSessionState.Guest -> AuthGateUiState.AppAccessible

                        is UserSessionState.Authenticated -> AuthGateUiState.Authenticated
                    }
                }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                AuthGateViewModel(application.appContainer.authRepository)
            }
        }
    }
}

sealed interface AuthGateUiState {
    data object Loading : AuthGateUiState

    data object LoginRequired : AuthGateUiState

    data object AppAccessible : AuthGateUiState

    data object Authenticated : AuthGateUiState
}
