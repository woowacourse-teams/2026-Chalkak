package com.stonefive.chalkak.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<LoginUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun login(provider: SocialLoginProvider) {
        execute { authRepository.login(provider) }
    }

    fun continueAsGuest() {
        execute { authRepository.continueAsGuest() }
    }

    private fun execute(block: suspend () -> AuthSession) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            val result = runCatching { block() }

            if (result.isSuccess) {
                _uiState.value = LoginUiState()
                _uiEvent.send(LoginUiEvent.NavigateToOnboarding)
            } else {
                _uiState.value = LoginUiState(error = result.exceptionOrNull())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                LoginViewModel(
                    authRepository = application.appContainer.authRepository,
                )
            }
        }
    }
}
