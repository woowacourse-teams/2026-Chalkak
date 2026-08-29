package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class PostDetail(
    val post: Post,
    val topic: String,
    val topicDate: LocalDate,
)
