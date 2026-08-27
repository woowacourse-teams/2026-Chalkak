package com.stonefive.chalkak.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.SocialSignUpFailure
import com.stonefive.chalkak.domain.model.SocialSignUpResult
import com.stonefive.chalkak.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SignUpViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<SignUpUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun completeSignUp(signaturePng: ByteArray) {
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.value = SignUpUiState(isSubmitting = true)
            try {
                when (val result = authRepository.completeSocialSignUp(signaturePng)) {
                    is SocialSignUpResult.Success -> {
                        _uiState.value = SignUpUiState()
                    }

                    is SocialSignUpResult.Failure -> handleFailure(result.reason)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = SignUpUiState(
                    errorMessage = "회원가입을 완료하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }

    private suspend fun handleFailure(failure: SocialSignUpFailure) {
        if (
            failure == SocialSignUpFailure.REAUTHENTICATION_REQUIRED ||
            failure == SocialSignUpFailure.MISSING_LOGIN_CONTEXT
        ) {
            _uiState.value = SignUpUiState()
            _uiEvent.send(SignUpUiEvent.NavigateToLogin)
            return
        }

        _uiState.value = SignUpUiState(errorMessage = failure.toMessage())
    }

    private fun SocialSignUpFailure.toMessage(): String = when (this) {
        SocialSignUpFailure.SIGNATURE_TOO_LARGE -> "사인 이미지가 1MB를 초과했어요."

        SocialSignUpFailure.SIGNATURE_PROCESSING_TIMEOUT -> "사인 처리에 시간이 걸리고 있어요. 다시 시도해 주세요."

        SocialSignUpFailure.SIGNATURE_NOT_FOUND -> "사인을 다시 업로드해 주세요."

        SocialSignUpFailure.INVALID_SIGNATURE -> "사용할 수 없는 사인이에요. 다시 그려주세요."

        SocialSignUpFailure.NETWORK_UNAVAILABLE -> "네트워크 연결을 확인해 주세요."

        SocialSignUpFailure.UNKNOWN -> "회원가입을 완료하지 못했어요. 다시 시도해 주세요."

        SocialSignUpFailure.MISSING_LOGIN_CONTEXT,
        SocialSignUpFailure.REAUTHENTICATION_REQUIRED,
        -> error("Handled before message mapping")
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                SignUpViewModel(application.appContainer.authRepository)
            }
        }
    }
}
