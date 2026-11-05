package com.stonefive.chalkak.feature.feedback

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 서버의 UTC ISO-8601 시각을 알림함과 같은 "M월 d일 HH:mm"(Asia/Seoul)으로 바꾼다.
 * 예: "2026-11-03T06:00:00Z" → "11월 3일 15:00". 읽지 못하면 원문을 그대로 돌려준다.
 */
internal fun String.toFeedbackTimeText(): String = runCatching {
    FEEDBACK_TIME_FORMATTER.format(Instant.parse(this).atZone(KOREA_TIME_ZONE))
}.getOrElse { this }

private val FEEDBACK_TIME_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
private val KOREA_TIME_ZONE = ZoneId.of("Asia/Seoul")
