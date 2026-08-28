package com.stonefive.chalkak.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.SignatureUpdateFailure
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignatureChangeViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SignatureChangeUiState())
    val uiState: StateFlow<SignatureChangeUiState> = _uiState.asStateFlow()

    fun updateSignature(signaturePng: ByteArray) {
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.value = SignatureChangeUiState(status = SignatureChangeStatus.Submitting)
            try {
                when (val result = userRepository.updateMySignature(signaturePng)) {
                    is SignatureUpdateResult.Success -> {
                        _uiState.value = SignatureChangeUiState(
                            status = SignatureChangeStatus.Completed(result.profile),
                        )
                    }

                    is SignatureUpdateResult.Failure -> {
                        _uiState.value = SignatureChangeUiState(
                            status = SignatureChangeStatus.Failed(result.reason.toMessage()),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = SignatureChangeUiState(
                    status = SignatureChangeStatus.Failed(
                        "사인을 저장하지 못했어요. 다시 시도해 주세요.",
                    ),
                )
            }
        }
    }

    private fun SignatureUpdateFailure.toMessage(): String = when (this) {
        SignatureUpdateFailure.SIGNATURE_TOO_LARGE -> "사인 이미지가 1MB를 초과했어요."
        SignatureUpdateFailure.INVALID_SIGNATURE -> "사용할 수 없는 사인이에요. 다시 그려주세요."
        SignatureUpdateFailure.NETWORK_UNAVAILABLE -> "네트워크 연결을 확인해 주세요."
        SignatureUpdateFailure.REAUTHENTICATION_REQUIRED -> "인증 정보가 만료되었어요. 다시 로그인해 주세요."
        SignatureUpdateFailure.SIGNATURE_NOT_FOUND -> "사인 업로드를 찾을 수 없어요. 다시 시도해 주세요."
        SignatureUpdateFailure.UNKNOWN -> "사인을 저장하지 못했어요. 다시 시도해 주세요."
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                SignatureChangeViewModel(application.appContainer.userRepository)
            }
        }
    }
}

data class SignatureChangeUiState(val status: SignatureChangeStatus = SignatureChangeStatus.Idle) {
    val isSubmitting: Boolean
        get() = status == SignatureChangeStatus.Submitting

    val errorMessage: String?
        get() = (status as? SignatureChangeStatus.Failed)?.message
}

sealed interface SignatureChangeStatus {
    data object Idle : SignatureChangeStatus

    data object Submitting : SignatureChangeStatus

    data class Completed(val profile: UserProfile) : SignatureChangeStatus

    data class Failed(val message: String) : SignatureChangeStatus
}
