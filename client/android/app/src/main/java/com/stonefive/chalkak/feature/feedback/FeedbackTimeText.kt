package com.stonefive.chalkak.feature.feedback

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

private val TODAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val YESTERDAY_FORMATTER = DateTimeFormatter.ofPattern("'어제' HH:mm")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private val KOREA_TIME_ZONE = ZoneId.of("Asia/Seoul")
