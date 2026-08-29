package com.stonefive.chalkak.data.remote.signature

import java.io.File

sealed interface UploadContent {
    data class Bytes(val value: ByteArray) : UploadContent

    data class FileContent(val file: File) : UploadContent
}

sealed interface PresignedUploadResult {
    data object Success : PresignedUploadResult

    data object NetworkFailure : PresignedUploadResult

    data object InvalidUploadUrl : PresignedUploadResult

    data object Rejected : PresignedUploadResult
}

interface PresignedImageUploader {
    suspend fun upload(
        uploadUrl: String,
        contentType: String,
        content: UploadContent,
    ): PresignedUploadResult
}

/**
 * Compatibility surface for the existing signature flows.
 *
 * New callers should depend on [PresignedImageUploader]. Keeping this adapter lets the
 * signature repositories retain their byte-array contract while sharing the same HTTP client.
 */
sealed interface SignatureUploadResult {
    data object Success : SignatureUploadResult

    data object NetworkFailure : SignatureUploadResult

    data object InvalidUploadUrl : SignatureUploadResult

    data object Rejected : SignatureUploadResult
}

interface SignatureUploader : PresignedImageUploader {
    suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult

    override suspend fun upload(
        uploadUrl: String,
        contentType: String,
        content: UploadContent,
    ): PresignedUploadResult {
        val bytes = content as? UploadContent.Bytes
            ?: return PresignedUploadResult.InvalidUploadUrl

        return when (upload(uploadUrl, bytes.value)) {
            SignatureUploadResult.Success -> PresignedUploadResult.Success
            SignatureUploadResult.NetworkFailure -> PresignedUploadResult.NetworkFailure
            SignatureUploadResult.InvalidUploadUrl -> PresignedUploadResult.InvalidUploadUrl
            SignatureUploadResult.Rejected -> PresignedUploadResult.Rejected
        }
    }
}
