package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class RecordPhoto(
    val date: LocalDate,
    val imageUrl: String,
    val signatureUrl: String,
    val contentDescription: String,
    val title: String?,
)
