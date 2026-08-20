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
) {
    val selectedPhoto: RecordPhoto?
        get() = photos.firstOrNull { it.date == selectedDate }
}

val INITIAL_RECORD_MONTH: YearMonth = YearMonth.of(2026, 8)
