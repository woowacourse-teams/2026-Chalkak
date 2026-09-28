package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.PostCalendarItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class RecordUiState(
    val month: YearMonth = INITIAL_RECORD_MONTH,
    val posts: List<PostCalendarItem> = emptyList(),
    val selectedDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isLoginRequired: Boolean = false,
    val pendingMessage: UiMessage? = null,
    val latestMonth: YearMonth = INITIAL_RECORD_MONTH,
    val availableMonths: List<YearMonth> = emptyList(),
) {
    val selectedPost: PostCalendarItem?
        get() = posts.firstOrNull { it.topicDate == selectedDate }

    val canGoPrevious: Boolean
        get() = !isLoading && availableMonths.any { it < month }

    val canGoNext: Boolean
        get() = !isLoading && availableMonths.any { it > month }
}

val INITIAL_RECORD_MONTH: YearMonth = YearMonth.now(ZoneId.of("Asia/Seoul"))
