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
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repository: PostRepository,
    initialMonth: YearMonth = INITIAL_RECORD_MONTH,
    latestMonth: YearMonth = INITIAL_RECORD_MONTH,
    private val currentMonthProvider: () -> YearMonth = {
        YearMonth.now(ZoneId.of("Asia/Seoul"))
    },
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordUiState(
            month = initialMonth,
            latestMonth = latestMonth,
        ),
    )
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var latestLoadGeneration = 0
    private var isRevalidating = false
    private var hasBeenPresented = false
    private var shouldRetryMonthList = false
    private var nextMessageId = 0L

    init {
        loadAvailableMonths(initialMonth)
    }

    fun moveToPreviousMonth() {
        val state = _uiState.value
        if (!state.canGoPrevious) return

        val previousMonth = state.availableMonths
            .filter { it < state.month }
            .maxOrNull()
            ?: return
        loadRecord(previousMonth)
    }

    fun moveToNextMonth() {
        val state = _uiState.value
        if (!state.canGoNext) return

        val nextMonth = state.availableMonths
            .filter { it > state.month }
            .minOrNull()
            ?: return
        loadRecord(nextMonth)
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (date !in state.posts.map { it.topicDate }) return

        _uiState.update { it.copy(selectedDate = date) }
    }

    fun retryCurrentMonth() {
        val state = _uiState.value
        if (shouldRetryMonthList || (state.availableMonths.isEmpty() && state.errorMessage != null)) {
            loadAvailableMonths(state.month)
        } else {
            loadRecord(state.month)
        }
    }

    fun onResume() {
        if (!hasBeenPresented) {
            hasBeenPresented = true
            return
        }
        revalidate()
    }

    fun revalidate() {
        val state = _uiState.value
        if (state.isLoading || isRevalidating) return

        var previousState = state.takeIf { it.errorMessage == null }
        isRevalidating = true
        val generation = ++latestLoadGeneration
        viewModelScope.launch {
            val monthsResult = runCatching { repository.getPostCalendarMonths() }
                .getOrElse { HomeResult.Failure(HomeFailure.Network) }
            if (generation != latestLoadGeneration) return@launch

            when (monthsResult) {
                is HomeResult.Success -> {
                    shouldRetryMonthList = false
                    val availableMonths = includeCurrentMonth(monthsResult.value)
                    previousState = previousState?.copy(availableMonths = availableMonths)
                    _uiState.update {
                        it.copy(availableMonths = availableMonths)
                    }
                }

                is HomeResult.Failure -> {
                    shouldRetryMonthList = true
                    val availableMonths = includeCurrentMonth(_uiState.value.availableMonths)
                    previousState = previousState?.copy(availableMonths = availableMonths)
                    _uiState.update {
                        val updatedState = it.copy(
                            availableMonths = availableMonths,
                        )
                        if (previousState != null) {
                            updatedState.copy(
                                pendingMessage = nextToast(monthsResult.reason.toRecordMessage()),
                            )
                        } else {
                            updatedState
                        }
                    }
                }
            }

            isRevalidating = false
            loadRecord(
                state.month,
                previousState = previousState,
                keepsContentVisible = previousState != null,
            )
        }
    }

    fun removeDeletedPost(postId: String) {
        _uiState.update { state ->
            val updatedPosts = state.posts.filterNot { it.postId == postId }
            val currentMonth = currentMonthProvider()
            val monthHasNoPosts = updatedPosts.none {
                it.topicDate.year == state.month.year &&
                    it.topicDate.month == state.month.month
            }
            val remainingMonths = if (monthHasNoPosts && state.month != currentMonth) {
                state.availableMonths.filterNot { it == state.month }
            } else {
                state.availableMonths
            }
            state.copy(
                posts = updatedPosts,
                availableMonths = includeCurrentMonth(remainingMonths),
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

    private fun loadRecord(
        month: YearMonth,
        previousState: RecordUiState? = null,
        keepsContentVisible: Boolean = false,
    ) {
        val generation = ++latestLoadGeneration
        isRevalidating = keepsContentVisible
        if (keepsContentVisible) {
            _uiState.update { it.copy(month = month) }
        } else {
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

                        is HomeResult.Failure -> if (previousState != null) {
                            _uiState.value = previousState.copy(
                                pendingMessage = nextToast(content.reason.toRecordMessage()),
                            )
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = content.reason.toRecordMessage(),
                                    isLoginRequired = content.reason == HomeFailure.Unauthorized,
                                )
                            }
                        }
                    }
                }.onFailure { error ->
                    if (generation != latestLoadGeneration) return@onFailure

                    if (previousState != null) {
                        _uiState.value = previousState.copy(
                            pendingMessage = nextToast(error.message ?: "기록을 불러오지 못했어요"),
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "기록을 불러오지 못했어요",
                                isLoginRequired = false,
                            )
                        }
                    }
                }
            isRevalidating = false
        }
    }

    private fun loadAvailableMonths(fallbackMonth: YearMonth) {
        val generation = ++latestLoadGeneration
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isLoginRequired = false,
            )
        }

        viewModelScope.launch {
            val result = runCatching { repository.getPostCalendarMonths() }
                .getOrElse { HomeResult.Failure(HomeFailure.Network) }
            if (generation != latestLoadGeneration) return@launch

            when (result) {
                is HomeResult.Success -> {
                    shouldRetryMonthList = false
                    val months = includeCurrentMonth(result.value)
                    val initialMonth = fallbackMonth
                    _uiState.update {
                        it.copy(
                            month = initialMonth,
                            latestMonth = maxOf(it.latestMonth, months.firstOrNull() ?: fallbackMonth),
                            availableMonths = months,
                        )
                    }
                    loadRecord(initialMonth)
                }

                is HomeResult.Failure -> {
                    shouldRetryMonthList = true
                    _uiState.update {
                        it.copy(availableMonths = includeCurrentMonth(emptyList()))
                    }
                    loadRecord(fallbackMonth)
                }
            }
        }
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )

    private fun includeCurrentMonth(months: List<YearMonth>): List<YearMonth> = (months + currentMonthProvider())
        .distinct()
        .sortedDescending()

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
