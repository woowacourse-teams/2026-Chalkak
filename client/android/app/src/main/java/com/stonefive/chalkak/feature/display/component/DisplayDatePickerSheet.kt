package com.stonefive.chalkak.feature.display.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.designsystem.component.picker.ChalkakPickerBottomSheet
import com.stonefive.chalkak.core.designsystem.component.picker.ChalkakWheelPicker
import java.time.LocalDate
import java.time.YearMonth

/**
 * 전시 화면의 년/월/일 휠 시트. [startDate](첫 주제 날짜)부터 [endDate](오늘)까지만 고를 수 있다.
 *
 * 년·월을 바꿨을 때 일이 범위를 벗어나면 가장 가까운 날로 맞춘다(예: 10월 22일에서 9월로 → 9월 22일).
 * 확인을 누르면 고른 날짜를 [onConfirm]으로 넘긴다.
 */
@Composable
fun DisplayDatePickerSheet(
    selectedDate: LocalDate,
    startDate: LocalDate,
    endDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDate by remember(selectedDate) {
        mutableStateOf(selectedDate.coerceIn(startDate, endDate))
    }
    val pickDate: (year: Int, month: Int, day: Int) -> Unit = { year, month, day ->
        pendingDate = displayPickerDate(year, month, day, startDate, endDate)
    }

    ChalkakPickerBottomSheet(
        title = "날짜 선택",
        onConfirm = { onConfirm(pendingDate) },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        ChalkakWheelPicker(
            items = (startDate.year..endDate.year).toList(),
            selectedItem = pendingDate.year,
            onSelectedItemChange = { pickDate(it, pendingDate.monthValue, pendingDate.dayOfMonth) },
            itemLabel = { "${it}년" },
            contentDescription = "연도",
            modifier = Modifier.weight(1f),
        )
        ChalkakWheelPicker(
            items = displayPickerMonths(pendingDate.year, startDate, endDate),
            selectedItem = pendingDate.monthValue,
            onSelectedItemChange = { pickDate(pendingDate.year, it, pendingDate.dayOfMonth) },
            itemLabel = { "${it}월" },
            contentDescription = "월",
            modifier = Modifier.weight(1f),
        )
        ChalkakWheelPicker(
            items = displayPickerDays(YearMonth.from(pendingDate), startDate, endDate),
            selectedItem = pendingDate.dayOfMonth,
            onSelectedItemChange = { pickDate(pendingDate.year, pendingDate.monthValue, it) },
            itemLabel = { "${it}일" },
            contentDescription = "일",
            modifier = Modifier.weight(1f),
        )
    }
}

/** [year]에서 고를 수 있는 월. 예: 시작 2026-09-17, 끝 2026-10-22 → [9, 10]. */
internal fun displayPickerMonths(
    year: Int,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Int> {
    val firstMonth = if (year == startDate.year) startDate.monthValue else 1
    val lastMonth = if (year == endDate.year) endDate.monthValue else 12
    return (firstMonth..lastMonth).toList()
}

/** [month]에서 고를 수 있는 일. 예: 시작 2026-09-17이면 2026년 9월 → [17..30]. */
internal fun displayPickerDays(
    month: YearMonth,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Int> {
    val firstDay = if (month == YearMonth.from(startDate)) startDate.dayOfMonth else 1
    val lastDay = if (month == YearMonth.from(endDate)) endDate.dayOfMonth else month.lengthOfMonth()
    return (firstDay..lastDay).toList()
}

/**
 * 년·월·일 조합을 실제로 고를 수 있는 날짜로 맞춘다.
 * 그 달에 없는 날(예: 9월 31일)은 말일로, 범위 밖은 [startDate]·[endDate]로 당긴다.
 */
internal fun displayPickerDate(
    year: Int,
    month: Int,
    day: Int,
    startDate: LocalDate,
    endDate: LocalDate,
): LocalDate {
    val yearMonth = YearMonth.of(year, month)
    return yearMonth
        .atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
        .coerceIn(startDate, endDate)
}
