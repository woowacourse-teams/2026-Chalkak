package com.stonefive.chalkak.feature.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        FeedbackUiState(content = savedStateHandle[KEY_CONTENT] ?: ""),
    )
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<FeedbackUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private var nextMessageId = 0L

    fun updateContent(content: String) {
        savedStateHandle[KEY_CONTENT] = content
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
                handleFailure(error)
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

    private fun handleFailure(error: FeedbackSubmissionException) {
        when (error.reason) {
            FeedbackSubmissionFailure.UNAUTHORIZED ->
                _uiEvent.trySend(FeedbackUiEvent.ReauthenticationRequired)

            // 400은 내용 오류와 접수 제한이 같은 코드라 서버 message로 안내한다.
            FeedbackSubmissionFailure.INVALID_CONTENT -> _uiState.update {
                it.copy(pendingMessage = nextToast(invalidContentMessage(error.serverMessage)))
            }

            // 타임아웃처럼 저장 여부를 알 수 없어 자동으로 다시 보내지 않고 목록 확인을 안내한다.
            FeedbackSubmissionFailure.NETWORK ->
                _uiState.update { it.copy(pendingMessage = nextToast(UNCERTAIN_SUBMISSION_MESSAGE)) }

            FeedbackSubmissionFailure.UNKNOWN ->
                _uiState.update { it.copy(pendingMessage = nextToast(SUBMISSION_ERROR_MESSAGE)) }
        }
    }

    /**
     * 400 안내 문구. message에 다음 접수 가능 시각이 있으면 접수 제한으로 보고 두 줄로 짧게 안내한다
     * (Android 12+ 시스템 토스트는 두 줄까지만 보여서 서버 문장을 그대로 쓰면 잘린다).
     * 시각이 없으면 서버 message 그대로, message가 없으면 기본 문구.
     */
    private fun invalidContentMessage(serverMessage: String?): String {
        if (serverMessage == null) return INVALID_CONTENT_MESSAGE
        val nextTime = findNextSubmissionTime(serverMessage) ?: return serverMessage
        return "$SUBMISSION_LIMITED_MESSAGE\n${nextTime.toNextSubmissionTimeText(clock)}부터 다시 보낼 수 있어요."
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )

    companion object {
        private const val KEY_CONTENT = "feedback_content"

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedbackViewModel(
                    feedbackRepository = application.appContainer.feedbackRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

private const val INVALID_CONTENT_MESSAGE = "피드백은 1자 이상 1000자 이하로 입력해 주세요."
private const val SUBMISSION_LIMITED_MESSAGE = "지금은 문의를 보낼 수 없어요."
private const val UNCERTAIN_SUBMISSION_MESSAGE = "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요."
private const val SUBMISSION_ERROR_MESSAGE = "피드백을 보내지 못했어요. 다시 시도해 주세요."
