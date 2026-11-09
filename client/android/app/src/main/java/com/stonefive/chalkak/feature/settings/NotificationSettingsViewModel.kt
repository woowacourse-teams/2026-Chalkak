package com.stonefive.chalkak.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(private val notificationRepository: NotificationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()
    private var nextMessageId = 0L

    init {
        loadPushSettings()
    }

    fun updateTopicPushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(topicPushEnabled = enabled))
    }

    fun updateModerationPushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(moderationPushEnabled = enabled))
    }

    fun updateAiReviewPushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(aiReviewPushEnabled = enabled))
    }

    fun updateLikePushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(likePushEnabled = enabled))
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

    private fun loadPushSettings() {
        viewModelScope.launch {
            when (val result = notificationRepository.getPushSettings()) {
                is NotificationResult.Success -> _uiState.update {
                    it.copy(pushSettings = result.value, isLoading = false)
                }

                is NotificationResult.Failure -> {
                    val pendingMessage = nextToast("푸시 알림 설정을 불러오지 못했어요.")
                    _uiState.update {
                        it.copy(isLoading = false, pendingMessage = pendingMessage)
                    }
                }
            }
        }
    }

    private fun updatePushSettings(update: NotificationPushSettingsUpdate) {
        val previous = _uiState.value.pushSettings ?: return
        val isTopicUpdate = update.topicPushEnabled != null
        val isModerationUpdate = update.moderationPushEnabled != null
        val isAiReviewUpdate = update.aiReviewPushEnabled != null
        val isLikeUpdate = update.likePushEnabled != null
        val isSaving = when {
            isTopicUpdate -> _uiState.value.isTopicPushSaving
            isModerationUpdate -> _uiState.value.isModerationPushSaving
            isAiReviewUpdate -> _uiState.value.isAiReviewPushSaving
            else -> _uiState.value.isLikePushSaving
        }
        if (isSaving) return

        val optimistic = previous.copy(
            topicPushEnabled = update.topicPushEnabled ?: previous.topicPushEnabled,
            moderationPushEnabled = update.moderationPushEnabled ?: previous.moderationPushEnabled,
            aiReviewPushEnabled = update.aiReviewPushEnabled ?: previous.aiReviewPushEnabled,
            likePushEnabled = update.likePushEnabled ?: previous.likePushEnabled,
        )
        _uiState.update {
            it.copy(
                pushSettings = optimistic,
                isTopicPushSaving = if (isTopicUpdate) true else it.isTopicPushSaving,
                isModerationPushSaving = if (isModerationUpdate) true else it.isModerationPushSaving,
                isAiReviewPushSaving = if (isAiReviewUpdate) true else it.isAiReviewPushSaving,
                isLikePushSaving = if (isLikeUpdate) true else it.isLikePushSaving,
            )
        }
        viewModelScope.launch {
            when (notificationRepository.updatePushSettings(update)) {
                is NotificationResult.Success -> _uiState.update {
                    it.copy(
                        isTopicPushSaving = if (isTopicUpdate) false else it.isTopicPushSaving,
                        isModerationPushSaving = if (isModerationUpdate) false else it.isModerationPushSaving,
                        isAiReviewPushSaving = if (isAiReviewUpdate) false else it.isAiReviewPushSaving,
                        isLikePushSaving = if (isLikeUpdate) false else it.isLikePushSaving,
                    )
                }

                is NotificationResult.Failure -> {
                    val pendingMessage = nextToast("푸시 알림 설정을 저장하지 못했어요.")
                    _uiState.update {
                        it.copy(
                            pushSettings = it.pushSettings?.copy(
                                topicPushEnabled = if (isTopicUpdate) {
                                    previous.topicPushEnabled
                                } else {
                                    it.pushSettings.topicPushEnabled
                                },
                                moderationPushEnabled = if (isModerationUpdate) {
                                    previous.moderationPushEnabled
                                } else {
                                    it.pushSettings.moderationPushEnabled
                                },
                                aiReviewPushEnabled = if (isAiReviewUpdate) {
                                    previous.aiReviewPushEnabled
                                } else {
                                    it.pushSettings.aiReviewPushEnabled
                                },
                                likePushEnabled = if (isLikeUpdate) {
                                    previous.likePushEnabled
                                } else {
                                    it.pushSettings.likePushEnabled
                                },
                            ),
                            isTopicPushSaving = if (isTopicUpdate) false else it.isTopicPushSaving,
                            isModerationPushSaving = if (isModerationUpdate) false else it.isModerationPushSaving,
                            isAiReviewPushSaving = if (isAiReviewUpdate) false else it.isAiReviewPushSaving,
                            isLikePushSaving = if (isLikeUpdate) false else it.isLikePushSaving,
                            pendingMessage = pendingMessage,
                        )
                    }
                }
            }
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
                NotificationSettingsViewModel(application.appContainer.notificationRepository)
            }
        }
    }
}
