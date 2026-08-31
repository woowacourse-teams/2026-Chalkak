package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.domain.model.PostCalendarItem
import java.time.LocalDate
import java.time.YearMonth

data class RecordUiState(
    val month: YearMonth = INITIAL_RECORD_MONTH,
    val posts: List<PostCalendarItem> = emptyList(),
    val selectedDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val latestMonth: YearMonth = INITIAL_RECORD_MONTH,
) {
    val selectedPost: PostCalendarItem?
        get() = posts.firstOrNull { it.topicDate == selectedDate }

    val canGoPrevious: Boolean
        get() = !isLoading

    val canGoNext: Boolean
        get() = !isLoading && month < latestMonth
}

val INITIAL_RECORD_MONTH: YearMonth = YearMonth.now()
