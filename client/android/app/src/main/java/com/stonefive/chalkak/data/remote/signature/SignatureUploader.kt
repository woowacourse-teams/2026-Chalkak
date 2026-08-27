package com.stonefive.chalkak.data.remote.signature

sealed interface SignatureUploadResult {
    data object Success : SignatureUploadResult

    data object NetworkFailure : SignatureUploadResult

    data object Rejected : SignatureUploadResult
}

interface SignatureUploader {
    suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult
}
