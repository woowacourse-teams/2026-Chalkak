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
import com.stonefive.chalkak.domain.model.PostCalendar
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

    // 홈 주간 기록에서 연 날짜. 재검증해도 이 날짜를 다시 고르고, 사용자가 날짜나 달을 바꾸면 비운다.
    private var requestedDate: LocalDate? = null
    private val calendarCache = mutableMapOf<YearMonth, PostCalendar>()
    private val monthLoadGenerations = mutableMapOf<YearMonth, Int>()
    private val deletedPostIds = mutableSetOf<String>()
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
        requestedDate = null
        loadRecord(previousMonth)
    }

    fun moveToNextMonth() {
        val state = _uiState.value
        if (!state.canGoNext) return

        val nextMonth = state.availableMonths
            .filter { it > state.month }
            .minOrNull()
            ?: return
        requestedDate = null
        loadRecord(nextMonth)
    }

    fun openDate(date: LocalDate) {
        requestedDate = date
        val state = _uiState.value
        val month = YearMonth.from(date)
        if (!state.isLoading && !isRevalidating && state.month == month &&
            state.posts.any { it.topicDate == date }
        ) {
            _uiState.update { it.copy(selectedDate = date) }
        } else {
            loadAvailableMonths(month)
        }
    }

    /** 년/월 휠에서 고른 달로 이동한다. 기록이 있는 달(+이번 달)만 받고, 같은 달이면 다시 불러오지 않는다. */
    fun moveToMonth(month: YearMonth) {
        val state = _uiState.value
        if (state.isLoading) return
        if (month == state.month || month !in state.availableMonths) return

        requestedDate = null
        loadRecord(month)
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (date !in state.posts.map { it.topicDate }) return

        requestedDate = null
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

        val keepsContentVisible = state.errorMessage == null
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
                    _uiState.update {
                        it.copy(availableMonths = availableMonths)
                    }
                }

                is HomeResult.Failure -> {
                    shouldRetryMonthList = true
                    val availableMonths = includeCurrentMonth(_uiState.value.availableMonths)
                    _uiState.update {
                        val updatedState = it.copy(
                            availableMonths = availableMonths,
                        )
                        if (keepsContentVisible) {
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
                keepsContentVisible = keepsContentVisible,
            )
        }
    }

    fun removeDeletedPost(postId: String) {
        // 삭제 전에 보낸 달력 요청이 삭제 뒤에 도착해도 이 게시물을 다시 보여주지 않도록 ID를 기억한다.
        deletedPostIds += postId
        calendarCache.replaceAll { _, calendar ->
            calendar.copy(posts = calendar.posts.filterNot { it.postId == postId })
        }
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
                    ?: updatedPosts.maxByOrNull { it.topicDate }?.topicDate,
            )
        }
        val hasRequestedPost = _uiState.value.posts
            .any { it.topicDate == requestedDate }
        if (!hasRequestedPost) requestedDate = null
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
        keepsContentVisible: Boolean = false,
    ) {
        val generation = ++latestLoadGeneration
        monthLoadGenerations[month] = generation
        val cachedCalendar = calendarCache[month]
        val hasVisibleContent = keepsContentVisible || cachedCalendar != null
        isRevalidating = hasVisibleContent
        if (cachedCalendar != null && !keepsContentVisible) {
            _uiState.update {
                val selectedDate = if (requestedDate != null) {
                    cachedCalendar.requestedTopicDate()
                } else {
                    cachedCalendar.latestTopicDate()
                }
                it.withCalendar(cachedCalendar, selectedDate)
            }
        } else if (!hasVisibleContent) {
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

        val selectedDateBeforeLoad = _uiState.value.selectedDate
        viewModelScope.launch {
            runCatching { repository.getPostCalendar(month) }
                .onSuccess { result ->
                    val content = if (result is HomeResult.Success) {
                        val calendar = result.value.copy(
                            posts = result.value.posts
                                .filterNot { it.postId in deletedPostIds },
                        )
                        // 다른 달로 넘어간 뒤 도착한 응답도 캐시에 담는다.
                        // 단, 같은 달에 더 나중에 보낸 요청이 있으면 이 응답이 그 결과를 덮어쓰지 않게 한다.
                        if (monthLoadGenerations[month] == generation) calendarCache[month] = calendar
                        HomeResult.Success(calendar)
                    } else {
                        result
                    }
                    if (generation != latestLoadGeneration) return@onSuccess

                    when (content) {
                        is HomeResult.Success -> {
                            _uiState.update {
                                // 홈 주간 기록에서 연 날짜는 그 날짜만 고르고, 사진이 없으면 다른 사진을 대신 열지 않는다.
                                // 그 외에는 요청 중에 사용자가 직접 고른 날짜를 유지하고,
                                // 고르지 않았으면 새 목록의 최신 날짜를 선택해 새로 올라온 사진이 보이게 한다.
                                val selectedDate = if (requestedDate != null) {
                                    content.value.requestedTopicDate()
                                } else {
                                    it.selectedDate?.takeIf { date ->
                                        date != selectedDateBeforeLoad &&
                                            content.value.posts
                                                .any { post -> post.topicDate == date }
                                    } ?: content.value.latestTopicDate()
                                }
                                it.withCalendar(content.value, selectedDate)
                            }
                        }

                        // 로그인이 풀렸으면 이전에 보던 기록 대신 로그인 화면을 보여준다.
                        is HomeResult.Failure -> if (hasVisibleContent && content.reason != HomeFailure.Unauthorized) {
                            _uiState.update {
                                it.copy(pendingMessage = nextToast(content.reason.toRecordMessage()))
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    posts = emptyList(),
                                    selectedDate = null,
                                    isLoading = false,
                                    errorMessage = content.reason.toRecordMessage(),
                                    isLoginRequired = content.reason == HomeFailure.Unauthorized,
                                )
                            }
                        }
                    }
                }.onFailure { error ->
                    if (generation != latestLoadGeneration) return@onFailure

                    if (hasVisibleContent) {
                        _uiState.update {
                            it.copy(pendingMessage = nextToast(error.message ?: "기록을 불러오지 못했어요"))
                        }
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
            if (generation == latestLoadGeneration) isRevalidating = false
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

    private fun RecordUiState.withCalendar(
        calendar: PostCalendar,
        selectedDate: LocalDate?,
    ): RecordUiState = copy(
        month = calendar.month,
        posts = calendar.posts,
        selectedDate = selectedDate,
        isLoading = false,
        errorMessage = null,
        isLoginRequired = false,
    )

    private fun PostCalendar.latestTopicDate(): LocalDate? = posts.maxByOrNull { it.topicDate }?.topicDate

    private fun PostCalendar.requestedTopicDate(): LocalDate? = requestedDate?.takeIf { date ->
        posts.any { it.topicDate == date }
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
