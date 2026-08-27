package com.stonefive.chalkak.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.SocialAuthFailure
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.model.SocialLoginResult
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun startCredentialRequest(): Boolean {
        if (!_uiState.value.canSubmit) return false
        _uiState.value = LoginUiState(status = LoginStatus.Loading)
        return true
    }

    fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ) {
        _uiState.value = LoginUiState(status = LoginStatus.Loading)
        viewModelScope.launch {
            try {
                when (val result = authRepository.login(provider, idToken)) {
                    is SocialLoginResult.LoginSuccess -> {
                        _uiState.value = LoginUiState(status = LoginStatus.Authenticated)
                    }

                    SocialLoginResult.SignUpRequired -> {
                        _uiState.value = LoginUiState(status = LoginStatus.SignUpRequired)
                    }

                    is SocialLoginResult.Failure -> {
                        _uiState.value = LoginUiState(
                            status = LoginStatus.Failed(result.reason.toMessage()),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = LoginUiState(
                    status = LoginStatus.Failed("로그인하지 못했어요. 다시 시도해 주세요."),
                )
            }
        }
    }

    fun credentialRequestCancelled() {
        _uiState.value = LoginUiState()
    }

    fun credentialRequestFailed(message: String) {
        _uiState.value = LoginUiState(status = LoginStatus.Failed(message))
    }

    fun showKakaoPreparing() {
        _uiState.value = LoginUiState(
            status = LoginStatus.Failed("카카오 로그인은 준비 중이에요."),
        )
    }

    fun signUpRequiredHandled() {
        if (_uiState.value.status == LoginStatus.SignUpRequired) {
            _uiState.value = LoginUiState()
        }
    }

    fun continueAsGuest() {
        if (!_uiState.value.canSubmit) return

        viewModelScope.launch {
            _uiState.value = LoginUiState(status = LoginStatus.Loading)
            try {
                authRepository.continueAsGuest()
                _uiState.value = LoginUiState(status = LoginStatus.GuestAccessGranted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = LoginUiState(
                    status = LoginStatus.Failed("화면을 불러오지 못했어요. 다시 시도해 주세요."),
                )
            }
        }
    }

    private fun SocialAuthFailure.toMessage(): String = when (this) {
        SocialAuthFailure.NETWORK_UNAVAILABLE -> "네트워크 연결을 확인해 주세요."

        SocialAuthFailure.UNAUTHORIZED -> "Google 계정을 확인할 수 없어요. 다시 시도해 주세요."

        SocialAuthFailure.UNSUPPORTED_PROVIDER -> "아직 지원하지 않는 로그인 방식이에요."

        SocialAuthFailure.INVALID_RESPONSE,
        SocialAuthFailure.UNKNOWN,
        -> "로그인하지 못했어요. 다시 시도해 주세요."
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
