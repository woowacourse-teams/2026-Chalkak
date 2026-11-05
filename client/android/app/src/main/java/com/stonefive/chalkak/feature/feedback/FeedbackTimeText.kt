package com.stonefive.chalkak.feature.feedback

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 서버의 UTC ISO-8601 시각을 고객센터 시각 문구(Asia/Seoul)로 바꾼다. 알림함 시각 형식과는 따로다.
 *
 * - 오늘: "HH:mm"
 * - 어제: "어제 HH:mm"
 * - 그 전(또는 미래): "M월 d일 HH:mm"
 *
 * 예: 지금이 서울 11월 5일 16:00이면 "2026-11-05T00:30:00Z" → "09:30",
 * "2026-11-04T09:20:00Z" → "어제 18:20", "2026-11-03T05:20:00Z" → "11월 3일 14:20".
 * 읽지 못하면 원문을 그대로 돌려준다.
 *
 * @param clock 오늘·어제를 가르는 지금 시각. 테스트에서 고정 시각을 넣는다.
 */
internal fun String.toFeedbackTimeText(clock: Clock = Clock.systemUTC()): String = runCatching {
    val time = Instant.parse(this).atZone(KOREA_TIME_ZONE)
    val today = LocalDate.now(clock.withZone(KOREA_TIME_ZONE))
    when (time.toLocalDate()) {
        today -> TODAY_FORMATTER.format(time)
        today.minusDays(1) -> YESTERDAY_FORMATTER.format(time)
        else -> DATE_FORMATTER.format(time)
    }
}.getOrElse { this }

/**
 * 접수 제한 안내 [message]에서 다음 접수 가능 시각(UTC ISO-8601 Instant)을 찾는다. 없거나 읽지 못하면 null.
 *
 * 접수 제한은 내용 오류와 같은 400 / BUSINESS_ERROR라 따로 구분할 코드가 없다.
 * 백엔드 API 문서에 "제한 초과 시 메시지에 다음 접수 가능 시각을 UTC Instant로 안내"라고 돼 있어,
 * 이 시각을 읽을 수 있을 때만 접수 제한으로 본다. 백엔드가 제한 전용 코드나 nextAvailableAt을 주면 그것으로 바꾼다.
 *
 * 예: "… 다음 접수 가능 시각(UTC): 2026-11-06T08:30:22Z" → 2026-11-06T08:30:22Z
 */
internal fun findNextSubmissionTime(message: String): Instant? = UTC_INSTANT_PATTERN
    .find(message)
    ?.let { runCatching { Instant.parse(it.value) }.getOrNull() }

/**
 * 다음 접수 가능 시각을 서울 기준 짧은 문구로 바꾼다. 초·나노초가 있으면 분을 올린다(그 전에는 아직 보낼 수 없으므로).
 *
 * - 오늘: "오늘 HH:mm"
 * - 내일: "내일 HH:mm"
 * - 그 밖: "M월 d일 HH:mm"
 *
 * 예: 지금이 서울 11월 6일 16:00이면 2026-11-06T08:30:22Z → "오늘 17:31", 2026-11-06T15:10:00Z → "내일 00:10".
 *
 * @param clock 오늘·내일을 가르는 지금 시각. 테스트에서 고정 시각을 넣는다.
 */
internal fun Instant.toNextSubmissionTimeText(clock: Clock = Clock.systemUTC()): String {
    val truncated = truncatedTo(ChronoUnit.MINUTES)
    val roundedUp = if (truncated == this) this else truncated.plus(1, ChronoUnit.MINUTES)
    val time = roundedUp.atZone(KOREA_TIME_ZONE)
    val today = LocalDate.now(clock.withZone(KOREA_TIME_ZONE))
    return when (time.toLocalDate()) {
        today -> NEXT_TODAY_FORMATTER.format(time)
        today.plusDays(1) -> NEXT_TOMORROW_FORMATTER.format(time)
        else -> DATE_FORMATTER.format(time)
    }
}

private val UTC_INSTANT_PATTERN = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z""")
private val NEXT_TODAY_FORMATTER = DateTimeFormatter.ofPattern("'오늘' HH:mm")
private val NEXT_TOMORROW_FORMATTER = DateTimeFormatter.ofPattern("'내일' HH:mm")
private val TODAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val YESTERDAY_FORMATTER = DateTimeFormatter.ofPattern("'어제' HH:mm")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private val KOREA_TIME_ZONE = ZoneId.of("Asia/Seoul")
