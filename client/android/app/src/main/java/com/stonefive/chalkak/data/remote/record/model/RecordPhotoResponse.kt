package com.stonefive.chalkak.data.remote.record.model

data class RecordPhotoResponse(
    val date: String,
    val imageUrl: String,
    val signatureUrl: String,
    val contentDescription: String,
    val title: String?,
)
