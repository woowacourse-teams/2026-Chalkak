package com.stonefive.chalkak.data.remote.record.model

data class RecordResponse(
    val month: String,
    val photos: List<RecordPhotoResponse>,
)
