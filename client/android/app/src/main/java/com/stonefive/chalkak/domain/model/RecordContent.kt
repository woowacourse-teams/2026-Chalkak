package com.stonefive.chalkak.domain.model

import java.time.YearMonth

data class RecordContent(
    val month: YearMonth,
    val photos: List<RecordPhoto>,
    val availableMonths: Set<YearMonth>,
)
