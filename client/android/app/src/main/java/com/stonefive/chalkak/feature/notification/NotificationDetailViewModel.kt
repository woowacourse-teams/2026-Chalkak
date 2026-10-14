package com.stonefive.chalkak.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationDetailViewModel(
    private val repository: NotificationRepository,
    private val notificationId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationDetailUiState())
    val uiState: StateFlow<NotificationDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = try {
                repository.getNotification(notificationId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NotificationResult.Failure(NotificationFailure.Network)
            }
            when (result) {
                is NotificationResult.Success -> _uiState.update {
                    it.copy(detail = result.value, isLoading = false, errorMessage = null)
                }

                is NotificationResult.Failure -> _uiState.update {
                    it.copy(
                        detail = null,
                        isLoading = false,
                        errorMessage = result.reason.toUserMessage(),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(notificationId: String) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                NotificationDetailViewModel(
                    repository = application.appContainer.notificationRepository,
                    notificationId = notificationId,
                )
            }
        }
    }
}

private fun NotificationFailure.toUserMessage(): String = when (this) {
    NotificationFailure.Network -> "네트워크 연결을 확인해 주세요."

    NotificationFailure.InvalidResponse -> "알림을 불러오지 못했어요."

    is NotificationFailure.Http -> when (statusCode) {
        401, 403 -> "로그인이 필요해요."
        404 -> "알림이 만료되었거나 게시물이 삭제됐어요."
        else -> "알림을 불러오지 못했어요."
    }
}
