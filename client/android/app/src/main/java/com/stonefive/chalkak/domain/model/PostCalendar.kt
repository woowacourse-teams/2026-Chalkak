package com.stonefive.chalkak.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class PostCalendar(
    val month: YearMonth,
    val posts: List<PostCalendarItem>,
)

data class PostCalendarItem(
    val postId: String,
    val topicDate: LocalDate,
    val thumbnailImageUrl: String,
    val status: PostStatus,
)

enum class PostStatus {
    APPROVED,
    PENDING,
    REJECTED,
    UNKNOWN,
}
