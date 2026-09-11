package com.stonefive.chalkak.data.remote.post

import java.io.File

sealed interface PostImageUploadResult {
    data object Success : PostImageUploadResult

    data object NetworkFailure : PostImageUploadResult

    data object InvalidUploadRequest : PostImageUploadResult

    data object Rejected : PostImageUploadResult
}

interface PostImageUploader {
    suspend fun upload(
        uploadUrl: String,
        contentType: String,
        imageFile: File,
    ): PostImageUploadResult
}
