package com.stonefive.chalkak.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.repository.PhotoColorRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.RecordPreferenceRepository
import com.stonefive.chalkak.feature.record.component.RecordPhotoStackStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val photoColorRepository: PhotoColorRepository? = null,
    private val preferenceRepository: RecordPreferenceRepository? = null,
    // 책자 속표지의 내 사인 주소. 받지 못하면 null.
    private val signatureLoader: suspend () -> String? = { null },
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordUiState(
            month = savedStateHandle
                .get<String>(KEY_MONTH)
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: initialMonth,
            latestMonth = latestMonth,
            selectedDate = savedSelectedDate(),
        ),
    )
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    // 홈 주간 기록에서 연 날짜. 재검증해도 이 날짜를 다시 고르고, 사용자가 날짜나 달을 바꾸면 비운다.
    private var requestedDate: LocalDate? = null
    private var restoredDate: LocalDate? = savedSelectedDate()
    private val calendarCache = mutableMapOf<YearMonth, PostCalendar>()
    private val monthLoadGenerations = mutableMapOf<YearMonth, Int>()
    private val deletedPostIds = mutableSetOf<String>()
    private var latestLoadGeneration = 0
    private var isRevalidating = false
    private var hasBeenPresented = false
    private var shouldRetryMonthList = false
    private var nextMessageId = 0L
    private val loadingYears = mutableSetOf<Int>()
    private val loadingTopicDates = mutableSetOf<LocalDate>()

    init {
        loadAvailableMonths(_uiState.value.month)
        preferenceRepository?.let { preferences ->
            viewModelScope.launch {
                val saved = runCatchingUnlessCancelled { preferences.getPhotoStackStyle() }
                    ?.let { name -> RecordPhotoStackStyle.entries.firstOrNull { it.name == name } }
                    ?: return@launch
                _uiState.update { it.copy(photoStackStyle = saved) }
            }
        }
    }

    /** 월간 사진 묶음 모양을 바꾸고, 다음에 열 때도 그 모양으로 보이게 저장한다. */
    fun togglePhotoStackStyle() {
        val style = _uiState.value.photoStackStyle.toggled
        _uiState.update { it.copy(photoStackStyle = style) }
        preferenceRepository?.let { preferences ->
            viewModelScope.launch { runCatchingUnlessCancelled { preferences.setPhotoStackStyle(style.name) } }
        }
    }

    /** 승인 전 사진은 피드에 보이지 않으므로(POST-007) 이동하지 않고 알려 준다. */
    fun onPendingPhotoSelected() {
        _uiState.update { it.copy(pendingMessage = nextToast("승인 전 사진은 아직 피드에서 볼 수 없어요")) }
    }

    fun moveToPreviousMonth() {
        val state = _uiState.value
        if (!state.canGoPrevious) return

        val previousMonth = state.availableMonths
            .filter { it < state.month }
            .maxOrNull()
            ?: return
        requestedDate = null
        restoredDate = null
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
        restoredDate = null
        loadRecord(nextMonth)
    }

    fun openDate(date: LocalDate) {
        requestedDate = date
        restoredDate = null
        savedStateHandle[KEY_MONTH] = YearMonth.from(date).toString()
        savedStateHandle[KEY_SELECTED_DATE] = date.toString()
        val state = _uiState.value
        val month = YearMonth.from(date)
        if (!state.isLoading && !isRevalidating && state.month == month &&
            state.posts.any { it.topicDate == date }
        ) {
            _uiState.update { it.copy(selectedDate = date) }
            saveSelectedDate()
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
        restoredDate = null
        loadRecord(month)
    }

    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (date !in state.posts.map { it.topicDate }) return

        requestedDate = null
        restoredDate = null
        _uiState.update { it.copy(selectedDate = date) }
        saveSelectedDate()
    }

    /**
     * 묶음에서 피드로 여는 사진의 날짜를 기억한다.
     * 피드에서 돌아오면 재검증이 최신 날짜를 고르는데, 이때만은 보던 날을 다시 펼치게 한다.
     */
    fun keepDateOnReturn(date: LocalDate) {
        if (_uiState.value.posts
                .none { it.topicDate == date }
        ) {
            return
        }

        requestedDate = null
        restoredDate = date
        _uiState.update { it.copy(selectedDate = date) }
        saveSelectedDate()
    }

    /** 연간 달력에서 고른 달을 연다. 기록이 없어도 지금 보던 달이면 다시 연다. 이미 받은 달이면 바로 그리고 뒤에서 최신화한다. */
    fun showMonth(month: YearMonth) {
        val state = _uiState.value
        requestedDate = null
        restoredDate = null
        loadRecord(minOf(month, state.latestMonth))
    }

    /** 해당 연도의 기록이 있는 지난 달을 불러오고, 사진 색을 채운다. */
    fun loadYear(year: Int) {
        if (!loadingYears.add(year)) return
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val months = state.availableMonths.filter {
                    it.year == year && it <= state.latestMonth && it !in state.yearPosts
                }
                val loaded = months
                    .map { month ->
                        async {
                            val result = runCatching { repository.getPostCalendar(month) }.getOrNull()
                            (result as? HomeResult.Success)?.let { success ->
                                month to success.value.posts
                                    .filterNot { it.postId in deletedPostIds }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
                _uiState.update { it.copy(yearPosts = it.yearPosts + loaded) }

                val posts = _uiState.value.yearPosts
                    .filterKeys { it.year == year }
                    .values
                    .flatten()
                loadPhotoColors(posts)
            } finally {
                loadingYears.remove(year)
            }
        }
    }

    /** 아직 색을 모르는 사진만 몇 장씩 나눠 구하고, 묶음마다 화면에 반영해 색이 차례로 채워지게 한다. */
    private suspend fun loadPhotoColors(posts: List<PostCalendarItem>) {
        val colorRepository = photoColorRepository ?: return
        val missing = posts.filter { it.postId !in _uiState.value.photoColors }
        missing.chunked(PHOTO_COLOR_BATCH_SIZE).forEach { chunk ->
            val colors = coroutineScope {
                chunk
                    .map { post ->
                        async {
                            runCatchingUnlessCancelled {
                                colorRepository.getPhotoColor(post.postId, post.thumbnailImageUrl)
                            }?.let { post.postId to it }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
            }
            _uiState.update { it.copy(photoColors = it.photoColors + colors) }
        }
    }

    /** 월간 책자에서 펼친 날 앞뒤의 주제를 불러온다. 실패한 날짜는 비워 두고 다음에 다시 펼칠 때 재시도한다. */
    fun loadTopicTitles(dates: List<LocalDate>) {
        val missing = dates.filter { it !in _uiState.value.topicTitles && loadingTopicDates.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            try {
                val titles = missing
                    .map { date ->
                        async {
                            runCatchingUnlessCancelled { repository.getTopicTitle(date) }?.let { date to it }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
                _uiState.update { it.copy(topicTitles = it.topicTitles + titles) }
            } finally {
                loadingTopicDates.removeAll(missing.toSet())
            }
        }
    }

    /** 월간 책자 속표지에 넣을 내 사인을 받는다. 받지 못하면 전에 받은 사인을 그대로 둔다. */
    fun loadSignature() {
        viewModelScope.launch {
            val url = runCatchingUnlessCancelled { signatureLoader() } ?: return@launch
            _uiState.update { if (it.signatureUrl == url) it else it.copy(signatureUrl = url) }
        }
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
        // 탭에 다시 들어올 때 설정에서 바꾼 사인도 함께 새로 받는다.
        loadSignature()
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
                yearPosts = state.yearPosts.mapValues { (_, posts) -> posts.filterNot { it.postId == postId } },
                availableMonths = includeCurrentMonth(remainingMonths),
                selectedDate = state.selectedDate
                    ?.takeIf { selectedDate -> updatedPosts.any { it.topicDate == selectedDate } }
                    ?: updatedPosts.maxByOrNull { it.topicDate }?.topicDate,
            )
        }
        val hasRequestedPost = _uiState.value.posts
            .any { it.topicDate == requestedDate }
        if (!hasRequestedPost) requestedDate = null
        saveSelectedDate()
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
        savedStateHandle[KEY_MONTH] = month.toString()
        if (month != _uiState.value.month && requestedDate?.let(YearMonth::from) != month) {
            savedStateHandle[KEY_SELECTED_DATE] = null
        }
        val generation = ++latestLoadGeneration
        monthLoadGenerations[month] = generation
        // 연간 달력에서 이미 받은 달이면 그 기록으로 먼저 그린다.
        val cachedCalendar = calendarCache[month]
            ?: _uiState.value.yearPosts[month]
                ?.let { PostCalendar(month = month, posts = it) }
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
            saveSelectedDate()
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
                                    } ?: restoredDate?.takeIf { date ->
                                        content.value.posts
                                            .any { post -> post.topicDate == date }
                                    } ?: content.value.latestTopicDate()
                                }
                                it.withCalendar(content.value, selectedDate)
                            }
                            restoredDate = null
                            savedStateHandle[KEY_MONTH] = content.value.month
                                .toString()
                            saveSelectedDate()
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
                            if (content.reason == HomeFailure.Unauthorized) {
                                restoredDate = null
                                saveSelectedDate()
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

    private fun savedSelectedDate(): LocalDate? = savedStateHandle
        .get<String>(KEY_SELECTED_DATE)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun saveSelectedDate() {
        savedStateHandle[KEY_SELECTED_DATE] = _uiState.value.selectedDate
            ?.toString()
    }

    private fun RecordUiState.withCalendar(
        calendar: PostCalendar,
        selectedDate: LocalDate?,
    ): RecordUiState = copy(
        month = calendar.month,
        posts = calendar.posts,
        // 받은 달은 연간 달력에도 채운다.
        yearPosts = yearPosts + (calendar.month to calendar.posts),
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
        private const val PHOTO_COLOR_BATCH_SIZE = 8
        private const val KEY_MONTH = "record_month"
        private const val KEY_SELECTED_DATE = "record_selected_date"

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                val container = application.appContainer
                RecordViewModel(
                    repository = container.postRepository,
                    savedStateHandle = createSavedStateHandle(),
                    photoColorRepository = container.photoColorRepository,
                    preferenceRepository = container.recordPreferenceRepository,
                    // 책자 속표지의 사인은 설정 화면과 같은 내 사인 API로 받는다.
                    signatureLoader = {
                        container.userRepository
                            .getMySignature()
                            .signatureUrl
                    },
                )
            }
        }
    }
}

/** 실패하면 null을 돌려준다. 취소는 삼키지 않고 그대로 전한다. */
private suspend fun <T> runCatchingUnlessCancelled(block: suspend () -> T?): T? = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
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
