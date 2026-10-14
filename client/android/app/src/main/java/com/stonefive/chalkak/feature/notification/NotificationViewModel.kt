package com.stonefive.chalkak.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationItem
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()
    private var nextMessageId = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch { loadFirstPage() }
        viewModelScope.launch { refreshUnreadStatus() }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasNext || state.isLoading || state.isLoadingMore) return

        _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = repository.getNotifications(
                    page = state.currentPage + 1,
                    pageSize = NOTIFICATION_PAGE_SIZE,
                )
            ) {
                is NotificationResult.Success -> _uiState.update { current ->
                    current.copy(
                        notifications =
                            current.notifications + result.value.notifications
                                .map(NotificationItem::toUiState),
                        currentPage = result.value.currentPage,
                        hasNext = result.value.hasNext,
                        isLoadingMore = false,
                    )
                }

                is NotificationResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.reason.toUserMessage())
                }
            }
        }
    }

    suspend fun markAsRead(notificationId: String): Boolean {
        val result = try {
            repository.markAsRead(notificationId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            NotificationResult.Failure(NotificationFailure.Network)
        }
        return when (result) {
            is NotificationResult.Success -> {
                _uiState.update { state ->
                    state.copy(
                        notifications = state.notifications.map { item ->
                            if (item.id == notificationId && item.readAt == null) {
                                item.copy(readAt = Instant.now().toString(), isUnread = false)
                            } else {
                                item
                            }
                        },
                    )
                }
                refreshUnreadStatus()
                true
            }

            is NotificationResult.Failure -> {
                val isNotFound = (result.reason as? NotificationFailure.Http)?.statusCode == 404
                if (isNotFound) {
                    _uiState.update {
                        it.copy(
                            errorMessage = null,
                            pendingMessage = UiMessage.Toast(nextMessageId++, "삭제된 게시물입니다"),
                        )
                    }
                }
                loadFirstPage()
                refreshUnreadStatus()
                if (!isNotFound) {
                    _uiState.update { it.copy(errorMessage = result.reason.toUserMessage()) }
                }
                false
            }
        }
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state ->
            if (state.pendingMessage?.id == messageId) state.copy(pendingMessage = null) else state
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            when (repository.markAllAsRead()) {
                is NotificationResult.Success -> refresh()

                is NotificationResult.Failure -> _uiState.update {
                    it.copy(errorMessage = "읽음 처리에 실패했어요. 다시 시도해 주세요.")
                }
            }
        }
    }

    private suspend fun loadFirstPage() {
        when (
            val result = repository.getNotifications(
                page = FIRST_NOTIFICATION_PAGE,
                pageSize = NOTIFICATION_PAGE_SIZE,
            )
        ) {
            is NotificationResult.Success -> _uiState.update {
                it.copy(
                    notifications = result.value.notifications
                        .map(NotificationItem::toUiState),
                    currentPage = result.value.currentPage,
                    hasNext = result.value.hasNext,
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = null,
                )
            }

            is NotificationResult.Failure -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = result.reason.toUserMessage(),
                )
            }
        }
    }

    private suspend fun refreshUnreadStatus() {
        when (val result = repository.getUnreadStatus()) {
            is NotificationResult.Success -> _uiState.update { it.copy(hasUnread = result.value) }
            is NotificationResult.Failure -> Unit
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                NotificationViewModel(application.appContainer.notificationRepository)
            }
        }
    }
}

private fun NotificationItem.toUiState(): NotificationItemUiState = NotificationItemUiState(
    id = id,
    title = title,
    body = body,
    timeText = createdAt.toNotificationTimeText(),
    readAt = readAt,
    isUnread = readAt == null,
    type = type,
    sourceType = sourceType,
    sourceId = sourceId,
    thumbnailModel = thumbnailImageUrl,
)

private fun String.toNotificationTimeText(): String = runCatching {
    NOTIFICATION_TIME_FORMATTER.format(Instant.parse(this).atZone(KOREA_TIME_ZONE))
}.getOrElse { this }

private fun NotificationFailure.toUserMessage(): String = when (this) {
    NotificationFailure.Network -> "네트워크 연결을 확인해 주세요."

    NotificationFailure.InvalidResponse -> "알림을 불러오지 못했어요."

    is NotificationFailure.Http -> when (statusCode) {
        401, 403 -> "로그인이 필요해요."
        404 -> "알림이 만료되었거나 삭제됐어요. 목록을 새로고침해 주세요."
        else -> "알림을 불러오지 못했어요."
    }
}

private val NOTIFICATION_TIME_FORMATTER = java.time.format.DateTimeFormatter
    .ofPattern("M월 d일 HH:mm")
private val KOREA_TIME_ZONE = java.time.ZoneId
    .of("Asia/Seoul")
private const val FIRST_NOTIFICATION_PAGE = 1
private const val NOTIFICATION_PAGE_SIZE = 20
