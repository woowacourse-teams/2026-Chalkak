package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.domain.model.PhotoEdit

data class PhotoUploadSubmission(
    val imageModel: String,
    val edit: PhotoEdit,
    val caption: String,
    val content: PhotoUploadSuccessContent,
)
