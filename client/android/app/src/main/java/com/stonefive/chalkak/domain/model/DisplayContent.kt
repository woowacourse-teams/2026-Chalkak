package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class DisplayContent(
    val selectedDate: LocalDate,
    val latestDate: LocalDate,
    val earliestDate: LocalDate,
    val topic: String,
    val photos: List<Post>,
    val featuredPhotos: List<Post>,
)
