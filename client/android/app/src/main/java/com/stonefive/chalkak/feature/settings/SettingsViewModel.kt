package com.stonefive.chalkak.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.NotificationRepository
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    versionName: String,
    private val notificationRepository: NotificationRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            versionName = versionName,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var nextMessageId = 0L
    private var signatureLoadJob: Job? = null

    init {
        loadPushSettings()
    }

    fun startLogin() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun showLogoutDialog() {
        _uiState.update { it.copy(accountDialog = SettingsAccountDialog.LOGOUT) }
    }

    fun showWithdrawDialog() {
        _uiState.update { it.copy(accountDialog = SettingsAccountDialog.WITHDRAW) }
    }

    fun applySignatureUpdate(signatureUrl: String) {
        val pendingMessage = nextToast("사인 반영까지 시간이 좀 걸릴 수 있어요!")
        _uiState.update {
            it.copy(
                signatureUrl = signatureUrl,
                pendingMessage = pendingMessage,
            )
        }
    }

    fun updateTopicPushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(topicPushEnabled = enabled))
    }

    fun updateModerationPushEnabled(enabled: Boolean) {
        updatePushSettings(NotificationPushSettingsUpdate(moderationPushEnabled = enabled))
    }

    fun dismissAccountDialog() {
        _uiState.update { it.copy(accountDialog = null) }
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

    fun confirmAccountAction() {
        val accountDialog = _uiState.value.accountDialog ?: return
        _uiState.update { it.copy(accountDialog = null) }

        when (accountDialog) {
            SettingsAccountDialog.LOGOUT -> clearProfile(authRepository::logout)
            SettingsAccountDialog.WITHDRAW -> clearProfile(userRepository::withdraw)
        }
    }

    private fun clearProfile(block: suspend () -> Unit) {
        if (_uiState.value.isAccountActionInProgress) return

        _uiState.update { it.copy(isAccountActionInProgress = true) }

        viewModelScope.launch {
            try {
                block()
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        isAccountActionInProgress = false,
                        signatureUrl = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val pendingMessage = nextToast(ACCOUNT_ACTION_ERROR_MESSAGE)
                _uiState.update {
                    it.copy(
                        isAccountActionInProgress = false,
                        pendingMessage = pendingMessage,
                    )
                }
            }
        }
    }

    fun refreshSignature() {
        if (signatureLoadJob?.isActive == true) return

        signatureLoadJob = viewModelScope.launch {
            if (authRepository.sessionState.value !is UserSessionState.Authenticated) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = false,
                        signatureUrl = null,
                    )
                }
                return@launch
            }

            try {
                val profile = userRepository.getMySignature()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        signatureUrl = profile.signatureThumbnailUrl,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UserProfileLoadException) {
                if (error.reason == UserProfileLoadFailure.UNAUTHORIZED) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            signatureUrl = null,
                        )
                    }
                } else {
                    val pendingMessage = nextToast(SIGNATURE_LOAD_ERROR_MESSAGE)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            pendingMessage = pendingMessage,
                        )
                    }
                }
            } catch (error: Exception) {
                val pendingMessage = nextToast(SIGNATURE_LOAD_ERROR_MESSAGE)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        pendingMessage = pendingMessage,
                    )
                }
            }
        }
    }

    private fun loadPushSettings() {
        val repository = notificationRepository ?: return
        if (authRepository.sessionState.value !is UserSessionState.Authenticated) return

        _uiState.update { it.copy(isPushSettingsLoading = true) }
        viewModelScope.launch {
            when (val result = repository.getPushSettings()) {
                is NotificationResult.Success -> _uiState.update {
                    it.copy(pushSettings = result.value, isPushSettingsLoading = false)
                }

                is NotificationResult.Failure -> {
                    val pendingMessage = nextToast("푸시 알림 설정을 불러오지 못했어요.")
                    _uiState.update {
                        it.copy(isPushSettingsLoading = false, pendingMessage = pendingMessage)
                    }
                }
            }
        }
    }

    private fun updatePushSettings(update: NotificationPushSettingsUpdate) {
        val repository = notificationRepository ?: return
        val previous = _uiState.value.pushSettings ?: return
        val isTopicUpdate = update.topicPushEnabled != null
        val isSaving = if (isTopicUpdate) {
            _uiState.value.isTopicPushSaving
        } else {
            _uiState.value.isModerationPushSaving
        }
        if (isSaving) return

        val optimistic = previous.copy(
            topicPushEnabled = update.topicPushEnabled ?: previous.topicPushEnabled,
            moderationPushEnabled = update.moderationPushEnabled ?: previous.moderationPushEnabled,
        )
        _uiState.update {
            it.copy(
                pushSettings = optimistic,
                isTopicPushSaving = if (isTopicUpdate) true else it.isTopicPushSaving,
                isModerationPushSaving = if (isTopicUpdate) it.isModerationPushSaving else true,
            )
        }
        viewModelScope.launch {
            when (repository.updatePushSettings(update)) {
                is NotificationResult.Success -> _uiState.update {
                    it.copy(
                        isTopicPushSaving = if (isTopicUpdate) false else it.isTopicPushSaving,
                        isModerationPushSaving = if (isTopicUpdate) it.isModerationPushSaving else false,
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
                                moderationPushEnabled = if (isTopicUpdate) {
                                    it.pushSettings.moderationPushEnabled
                                } else {
                                    previous.moderationPushEnabled
                                },
                            ),
                            isTopicPushSaving = if (isTopicUpdate) false else it.isTopicPushSaving,
                            isModerationPushSaving = if (isTopicUpdate) it.isModerationPushSaving else false,
                            pendingMessage = pendingMessage,
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                SettingsViewModel(
                    authRepository = application.appContainer.authRepository,
                    userRepository = application.appContainer.userRepository,
                    versionName = BuildConfig.VERSION_NAME,
                    notificationRepository = application.appContainer.notificationRepository,
                )
            }
        }
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )
}

private const val SIGNATURE_LOAD_ERROR_MESSAGE = "사인을 불러오지 못했어요. 다시 시도해 주세요."
private const val ACCOUNT_ACTION_ERROR_MESSAGE = "요청을 처리하지 못했어요. 다시 시도해 주세요."
