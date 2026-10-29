package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.PhotoColor
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.feature.record.component.RecordPhotoStackStyle
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
    /** 월간 책자 왼쪽 면에 적을 날짜별 주제. 날짜가 바뀌지 않는 값이라 월을 옮겨도 유지한다. */
    val topicTitles: Map<LocalDate, String> = emptyMap(),
    /** 연간 달력에 쓰는 월별 게시물. 한 번 불러온 달은 다시 부르지 않는다. */
    val yearPosts: Map<YearMonth, List<PostCalendarItem>> = emptyMap(),
    /** 연간 달력 칸에 칠할 게시물별 대표색. */
    val photoColors: Map<String, PhotoColor> = emptyMap(),
    /** 월간 책자 속표지에 넣을 내 사인. 아직 받지 못했거나 사인이 없으면 null. */
    val signatureUrl: String? = null,
    /** 월간 상단 사진 묶음의 모양. */
    val photoStackStyle: RecordPhotoStackStyle = RecordPhotoStackStyle.PILLARS,
) {
    val selectedPost: PostCalendarItem?
        get() = posts.firstOrNull { it.topicDate == selectedDate }

    val canGoPrevious: Boolean
        get() = !isLoading && availableMonths.any { it < month }

    val canGoNext: Boolean
        get() = !isLoading && availableMonths.any { it > month }

    /** 연간 달력에서 이동할 수 있는 가장 이른 해. 기록이 없으면 최신 연도다. */
    val earliestYear: Int
        get() = minOf(availableMonths.minOfOrNull { it.year } ?: latestMonth.year, latestMonth.year)

    /** 년/월 휠을 열 수 있는지. 휠에는 [availableMonths]만 보인다. */
    val canPickMonth: Boolean
        get() = !isLoading && availableMonths.isNotEmpty()
}

val INITIAL_RECORD_MONTH: YearMonth = YearMonth.now(ZoneId.of("Asia/Seoul"))
