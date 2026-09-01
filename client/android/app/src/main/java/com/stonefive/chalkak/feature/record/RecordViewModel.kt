package com.stonefive.chalkak.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repository: PostRepository,
    initialMonth: YearMonth = INITIAL_RECORD_MONTH,
    latestMonth: YearMonth = INITIAL_RECORD_MONTH,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordUiState(
            month = initialMonth,
            latestMonth = latestMonth,
        ),
    )
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var latestLoadGeneration = 0
    private var nextMessageId = 0L

    init {
        loadRecord(initialMonth)
    }

    fun moveToPreviousMonth() {
        val state = _uiState.value
        if (!state.canGoPrevious) return

        loadRecord(state.month.minusMonths(1))
    }

    fun moveToNextMonth() {
        val state = _uiState.value
        if (!state.canGoNext) return

        loadRecord(state.month.plusMonths(1))
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (date !in state.posts.map { it.topicDate }) return

        _uiState.update { it.copy(selectedDate = date) }
    }

    fun retryCurrentMonth() {
        loadRecord(_uiState.value.month)
    }

    fun removeDeletedPost(postId: String) {
        _uiState.update { state ->
            val updatedPosts = state.posts.filterNot { it.postId == postId }
            state.copy(
                posts = updatedPosts,
                selectedDate = state.selectedDate
                    ?.takeIf { selectedDate -> updatedPosts.any { it.topicDate == selectedDate } }
                    ?: updatedPosts.firstOrNull()?.topicDate,
            )
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

    fun onCalendarImageSaved(saved: Boolean) {
        val message = if (saved) {
            "달력을 이미지로 저장했어요"
        } else {
            "이미지 저장에 실패했어요"
        }
        _uiState.update { it.copy(pendingMessage = nextToast(message)) }
    }

    fun onStoragePermissionDenied() {
        _uiState.update {
            it.copy(pendingMessage = nextToast("이미지 저장 권한이 필요해요"))
        }
    }

    private fun loadRecord(month: YearMonth) {
        val generation = ++latestLoadGeneration
        _uiState.update {
            it.copy(
                month = month,
                posts = emptyList(),
                selectedDate = null,
                isLoading = true,
                errorMessage = null,
                isLoginRequired = false,
            )
        }

        viewModelScope.launch {
            runCatching { repository.getPostCalendar(month) }
                .onSuccess { content ->
                    if (generation != latestLoadGeneration) return@onSuccess

                    when (content) {
                        is HomeResult.Success -> _uiState.update {
                            it.copy(
                                month = content.value.month,
                                posts = content.value.posts,
                                selectedDate = content.value.posts
                                    .firstOrNull()
                                    ?.topicDate,
                                isLoading = false,
                                errorMessage = null,
                                isLoginRequired = false,
                            )
                        }

                        is HomeResult.Failure -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = content.reason.toRecordMessage(),
                                isLoginRequired = content.reason == HomeFailure.Unauthorized,
                            )
                        }
                    }
                }.onFailure { error ->
                    if (generation != latestLoadGeneration) return@onFailure

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "기록을 불러오지 못했어요",
                            isLoginRequired = false,
                        )
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
                RecordViewModel(
                    repository = application.appContainer.postRepository,
                )
            }
        }
    }
}

private fun HomeFailure.toRecordMessage(): String = when (this) {
    HomeFailure.Unauthorized -> "로그인이 필요해요"

    is HomeFailure.Http -> if (statusCode == 400) {
        "조회할 수 없는 연월이에요"
    } else {
        "기록을 불러오지 못했어요"
    }

    else -> "기록을 불러오지 못했어요"
}
