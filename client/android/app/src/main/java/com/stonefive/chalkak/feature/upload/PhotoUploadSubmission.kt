package com.stonefive.chalkak.feature.upload

data class PhotoUploadSubmission(
    val imageModel: String,
    val caption: String,
    val content: PhotoUploadSuccessContent,
)
