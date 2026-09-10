package com.stonefive.chalkak.feature.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoUploadEntryGateViewModel(
    private val repository: PhotoUploadEntryRepository,
    private val sessionState: StateFlow<UserSessionState>,
    private val launchContext: CoroutineContext = EmptyCoroutineContext,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoUploadEntryGateUiState())
    val uiState: StateFlow<PhotoUploadEntryGateUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<PhotoUploadEntryGateUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private var nextMessageId = 0L

    fun openPhotoUpload() {
        if (_uiState.value.isChecking) return

        if (sessionState.value !is UserSessionState.Authenticated) {
            _uiState.update { it.copy(pendingMessage = nextToast(PHOTO_UPLOAD_LOGIN_REQUIRED_MESSAGE)) }
            return
        }

        _uiState.update { it.copy(isChecking = true) }
        viewModelScope.launch(launchContext) {
            when (val result = repository.getTodayPostStatus()) {
                is TodayPostStatusResult.Success -> {
                    if (result.value.isPosted) {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                pendingMessage = nextToast(ALREADY_POSTED_MESSAGE),
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isChecking = false) }
                        _uiEvent.send(PhotoUploadEntryGateUiEvent.OpenPhotoUpload(result.value.topicDate))
                    }
                }

                is TodayPostStatusResult.Failure -> handleFailure(result.reason)
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

    private suspend fun handleFailure(reason: TodayPostStatusFailure) {
        _uiState.update { it.copy(isChecking = false) }
        when (reason) {
            TodayPostStatusFailure.ReauthenticationRequired ->
                _uiEvent.send(PhotoUploadEntryGateUiEvent.NavigateToLogin)

            TodayPostStatusFailure.NoOpenTopic ->
                showToast(NO_OPEN_TOPIC_MESSAGE)

            TodayPostStatusFailure.Suspended ->
                showToast(SUSPENDED_MEMBER_MESSAGE)

            TodayPostStatusFailure.Network,
            TodayPostStatusFailure.InvalidResponse,
            is TodayPostStatusFailure.Http,
            -> showToast(TODAY_POST_STATUS_ERROR_MESSAGE)
        }
    }

    private fun showToast(text: String) {
        _uiState.update { it.copy(pendingMessage = nextToast(text)) }
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                PhotoUploadEntryGateViewModel(
                    repository = application.appContainer.photoUploadEntryRepository,
                    sessionState = application.appContainer.authRepository.sessionState,
                )
            }
        }
    }
}

data class PhotoUploadEntryGateUiState(
    val isChecking: Boolean = false,
    val pendingMessage: UiMessage? = null,
)

sealed interface PhotoUploadEntryGateUiEvent {
    data class OpenPhotoUpload(val topicDate: LocalDate) : PhotoUploadEntryGateUiEvent

    data object NavigateToLogin : PhotoUploadEntryGateUiEvent
}

const val ALREADY_POSTED_MESSAGE = "이미 이 주제에 게시물을 작성했어요."
const val NO_OPEN_TOPIC_MESSAGE = "지금 참여할 수 있는 주제가 없어요."
const val SUSPENDED_MEMBER_MESSAGE = "이용이 정지되어 게시물을 작성할 수 없어요."
const val TODAY_POST_STATUS_ERROR_MESSAGE = "게시물 작성 여부를 확인하지 못했어요. 다시 시도해 주세요."
const val PHOTO_UPLOAD_LOGIN_REQUIRED_MESSAGE = "게시물을 추가하려면 로그인이 필요해요"
