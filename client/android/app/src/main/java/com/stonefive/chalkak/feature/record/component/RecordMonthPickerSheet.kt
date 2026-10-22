package com.stonefive.chalkak.feature.record.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.core.designsystem.component.picker.ChalkakPickerBottomSheet
import com.stonefive.chalkak.core.designsystem.component.picker.ChalkakWheelPicker
import java.time.YearMonth
import kotlin.math.abs

/**
 * 기록 화면의 년/월 휠 시트. 기록이 있는 달([availableMonths])만 고를 수 있다.
 *
 * 확인을 누르면 고른 달을 [onConfirm]으로 넘긴다. 같은 달인지 거르는 일은 ViewModel이 한다.
 */
@Composable
fun RecordMonthPickerSheet(
    month: YearMonth,
    availableMonths: List<YearMonth>,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 현재 달이 목록에 없으면(월 목록 조회 실패 등) 휠이 비지 않도록 함께 보여준다.
    val selectableMonths = remember(month, availableMonths) {
        (availableMonths + month).distinct().sorted()
    }
    var pendingMonth by remember(month) { mutableStateOf(month) }

    ChalkakPickerBottomSheet(
        title = "날짜 선택",
        onConfirm = { onConfirm(pendingMonth) },
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        ChalkakWheelPicker(
            items = selectableMonths.map { it.year }.distinct(),
            selectedItem = pendingMonth.year,
            onSelectedItemChange = { year ->
                pendingMonth = closestRecordMonthInYear(selectableMonths, year, pendingMonth.monthValue)
            },
            itemLabel = { "${it}년" },
            contentDescription = "연도",
            modifier = Modifier.weight(1f),
        )
        ChalkakWheelPicker(
            items = selectableMonths.filter { it.year == pendingMonth.year },
            selectedItem = pendingMonth,
            onSelectedItemChange = { pendingMonth = it },
            itemLabel = { "${it.monthValue}월" },
            contentDescription = "월",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * [year]의 고를 수 있는 달 중 [preferredMonth]와 가장 가까운 달.
 * 예: 2026년에 9·10월만 있으면 `preferredMonth = 3` → 2026년 9월.
 */
internal fun closestRecordMonthInYear(
    selectableMonths: List<YearMonth>,
    year: Int,
    preferredMonth: Int,
): YearMonth = selectableMonths
    .filter { it.year == year }
    .minBy { abs(it.monthValue - preferredMonth) }
