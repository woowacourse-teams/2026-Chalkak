package com.stonefive.chalkak.data.remote.signature

import okhttp3.OkHttpClient

class OkHttpSignatureUploader(client: OkHttpClient) : SignatureUploader {
    private val delegate = OkHttpPresignedImageUploader(client)

    override suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult = when (
        delegate.upload(
            uploadUrl = uploadUrl,
            contentType = PNG_CONTENT_TYPE,
            content = UploadContent.Bytes(signaturePng),
        )
    ) {
        PresignedUploadResult.Success -> SignatureUploadResult.Success
        PresignedUploadResult.NetworkFailure -> SignatureUploadResult.NetworkFailure
        PresignedUploadResult.InvalidUploadUrl -> SignatureUploadResult.InvalidUploadUrl
        PresignedUploadResult.Rejected -> SignatureUploadResult.Rejected
    }

    private companion object {
        const val PNG_CONTENT_TYPE = "image/png"
    }
}
