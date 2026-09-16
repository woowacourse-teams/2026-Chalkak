package com.stonefive.chalkak.feature.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedbackViewModel(private val feedbackRepository: FeedbackRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<FeedbackUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private var nextMessageId = 0L

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                feedbackRepository.submitFeedback(state.content.trim())
                _uiState.update { it.copy(isSubmitting = false) }
                _uiEvent.trySend(FeedbackUiEvent.Submitted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: FeedbackSubmissionException) {
                _uiState.update { it.copy(isSubmitting = false) }
                handleFailure(error.reason)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingMessage = nextToast(SUBMISSION_ERROR_MESSAGE),
                    )
                }
            }
        }
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state ->
            if (state.pendingMessage?.id == messageId) {
                state.copy(pendingMessage = null)
            } else {
                state
            }
        }
    }

    private fun handleFailure(reason: FeedbackSubmissionFailure) {
        when (reason) {
            FeedbackSubmissionFailure.UNAUTHORIZED ->
                _uiEvent.trySend(FeedbackUiEvent.ReauthenticationRequired)

            FeedbackSubmissionFailure.INVALID_CONTENT ->
                _uiState.update { it.copy(pendingMessage = nextToast(INVALID_CONTENT_MESSAGE)) }

            FeedbackSubmissionFailure.NETWORK,
            FeedbackSubmissionFailure.UNKNOWN,
            ->
                _uiState.update { it.copy(pendingMessage = nextToast(SUBMISSION_ERROR_MESSAGE)) }
        }
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedbackViewModel(
                    feedbackRepository = application.appContainer.feedbackRepository,
                )
            }
        }
    }
}

private const val INVALID_CONTENT_MESSAGE = "피드백은 1자 이상 1000자 이하로 입력해 주세요."
private const val SUBMISSION_ERROR_MESSAGE = "피드백을 보내지 못했어요. 다시 시도해 주세요."
