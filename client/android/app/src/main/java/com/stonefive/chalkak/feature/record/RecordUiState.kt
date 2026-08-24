package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.domain.model.RecordPhoto
import java.time.LocalDate
import java.time.YearMonth

data class RecordUiState(
    val month: YearMonth = INITIAL_RECORD_MONTH,
    val photos: List<RecordPhoto> = emptyList(),
    val selectedDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val availableMonths: Set<YearMonth> = emptySet(),
) {
    val selectedPhoto: RecordPhoto?
        get() = photos.firstOrNull { it.date == selectedDate }

    val canGoPrevious: Boolean
        get() = !isLoading && month.minusMonths(1) in availableMonths

    val canGoNext: Boolean
        get() = !isLoading && month.plusMonths(1) in availableMonths
}

val INITIAL_RECORD_MONTH: YearMonth = YearMonth.now()
