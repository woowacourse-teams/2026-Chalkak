package com.stonefive.chalkak.feature.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 문의 한 건([feedbackId])의 상세와 삭제. 만들어지면 상세를 불러온다.
 *
 * 결과는 [FeedbackDetailUiState.status]로 내보낸다. 404면 [FeedbackDetailStatus.NotFound],
 * 삭제 204면 [FeedbackDetailStatus.Deleted], 401이면 [FeedbackDetailStatus.ReauthenticationRequired]다.
 */
class FeedbackDetailViewModel(
    private val feedbackId: String,
    private val repository: FeedbackRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedbackDetailUiState())
    val uiState: StateFlow<FeedbackDetailUiState> = _uiState.asStateFlow()

    private var nextMessageId = 0L

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(status = FeedbackDetailStatus.Loading) }
        viewModelScope.launch {
            try {
                val detail = repository.getFeedbackDetail(feedbackId)
                _uiState.update {
                    it.copy(status = FeedbackDetailStatus.Content, detail = detail.toUiState())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(status = error.toFailureStatus() ?: FeedbackDetailStatus.Failed) }
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isDeleting || state.status != FeedbackDetailStatus.Content) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                repository.deleteFeedback(feedbackId)
                _uiState.update { it.copy(status = FeedbackDetailStatus.Deleted, isDeleting = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failureStatus = error.toFailureStatus()
                _uiState.update {
                    if (failureStatus != null) {
                        it.copy(status = failureStatus, isDeleting = false)
                    } else {
                        it.copy(
                            isDeleting = false,
                            pendingMessage = UiMessage.Toast(nextMessageId++, DELETE_FAILED_MESSAGE),
                        )
                    }
                }
            }
        }
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state ->
            if (state.pendingMessage?.id == messageId) state.copy(pendingMessage = null) else state
        }
    }

    companion object {
        fun factory(feedbackId: String) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedbackDetailViewModel(
                    feedbackId = feedbackId,
                    repository = application.appContainer.feedbackRepository,
                )
            }
        }
    }
}

/** 404·401이면 화면을 떠나야 하는 상태로, 그 밖에는 null(화면에서 처리)로 바꾼다. */
private fun Exception.toFailureStatus(): FeedbackDetailStatus? = when ((this as? FeedbackInquiryException)?.reason) {
    FeedbackInquiryFailure.NOT_FOUND -> FeedbackDetailStatus.NotFound(NOT_FOUND_MESSAGE)
    FeedbackInquiryFailure.UNAUTHORIZED -> FeedbackDetailStatus.ReauthenticationRequired
    else -> null
}

private fun FeedbackInquiryDetail.toUiState(): FeedbackDetailContentUiState = FeedbackDetailContentUiState(
    content = content,
    createdAtText = createdAt.toFeedbackTimeText(),
    answerContent = answer?.content,
    answeredAtText = answer?.answeredAt?.toFeedbackTimeText(),
)

private const val NOT_FOUND_MESSAGE = "문의를 찾을 수 없어요"
private const val DELETE_FAILED_MESSAGE = "문의를 삭제하지 못했어요. 다시 시도해 주세요."
