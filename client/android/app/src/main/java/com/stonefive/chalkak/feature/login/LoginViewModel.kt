package com.stonefive.chalkak.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessageEmitter
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
    private val messageEmitter = UiMessageEmitter()
    val uiMessage = messageEmitter.messages

    fun startCredentialRequest(provider: SocialLoginProvider): Boolean {
        if (!_uiState.value.canSubmit) return false
        _uiState.value = LoginUiState(
            status = LoginStatus.Loading,
            activeProvider = provider,
        )
        return true
    }

    fun login(
        provider: SocialLoginProvider,
        idToken: String,
    ) {
        _uiState.value = LoginUiState(
            status = LoginStatus.Loading,
            activeProvider = provider,
        )
        viewModelScope.launch {
            try {
                when (val result = authRepository.login(provider, idToken)) {
                    is SocialLoginResult.LoginSuccess -> {
                        _uiState.value = LoginUiState(
                            status = LoginStatus.Authenticated,
                            activeProvider = provider,
                        )
                    }

                    SocialLoginResult.SignUpRequired -> {
                        _uiState.value = LoginUiState(
                            status = LoginStatus.SignUpRequired,
                            activeProvider = provider,
                        )
                    }

                    is SocialLoginResult.Failure -> {
                        showFailure(result.reason.toMessage(provider))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showFailure("로그인하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    fun credentialRequestCancelled() {
        _uiState.value = LoginUiState()
    }

    fun credentialRequestFailed(message: String) {
        showFailure(message)
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
                showFailure("화면을 불러오지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    private fun showFailure(message: String) {
        _uiState.value = LoginUiState()
        messageEmitter.showToast(message)
    }

    private fun SocialAuthFailure.toMessage(provider: SocialLoginProvider): String = when (this) {
        SocialAuthFailure.NETWORK_UNAVAILABLE -> "네트워크 연결을 확인해 주세요."

        SocialAuthFailure.UNAUTHORIZED ->
            "${provider.displayName} 계정을 확인할 수 없어요. 다시 시도해 주세요."

        SocialAuthFailure.UNSUPPORTED_PROVIDER -> "아직 지원하지 않는 로그인 방식이에요."

        SocialAuthFailure.INVALID_RESPONSE,
        SocialAuthFailure.UNKNOWN,
        -> "로그인하지 못했어요. 다시 시도해 주세요."
    }

    private val SocialLoginProvider.displayName: String
        get() = when (this) {
            SocialLoginProvider.GOOGLE -> "Google"
            SocialLoginProvider.KAKAO -> "카카오"
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
