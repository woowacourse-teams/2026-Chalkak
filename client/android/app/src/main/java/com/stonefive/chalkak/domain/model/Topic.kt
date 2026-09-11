package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class Topic(
    val id: String,
    val title: String,
    val date: LocalDate,
)
