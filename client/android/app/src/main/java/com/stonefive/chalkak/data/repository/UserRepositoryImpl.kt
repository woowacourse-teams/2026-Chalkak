package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.signature.PresignedImageUploader
import com.stonefive.chalkak.data.remote.signature.PresignedUploadResult
import com.stonefive.chalkak.data.remote.signature.UploadContent
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.domain.model.SignatureUpdateFailure
import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.repository.UserRepository

class UserRepositoryImpl(
    private val userDataSource: UserDataSource,
    private val signatureUploader: PresignedImageUploader,
) : UserRepository {
    override suspend fun getMySignature(): UserProfile = when (
        val result = userDataSource.getMySignature()
    ) {
        is ApiResult.Success -> UserProfile(
            signatureUrl = result.value.originalImageUrl,
            signatureThumbnailUrl = result.value.thumbnailImageUrl,
        )

        is ApiResult.Failure -> throw UserProfileLoadException(result.error.toProfileLoadFailure())
    }

    override suspend fun updateMySignature(signaturePng: ByteArray): SignatureUpdateResult {
        if (signaturePng.size > MAX_SIGNATURE_BYTES) {
            return SignatureUpdateResult.Failure(SignatureUpdateFailure.SIGNATURE_TOO_LARGE)
        }

        val upload = when (val result = userDataSource.createSignatureUpload()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return SignatureUpdateResult.Failure(result.error.toUpdateFailure())
        }

        when (
            signatureUploader.upload(
                uploadUrl = upload.uploadUrl,
                contentType = SIGNATURE_CONTENT_TYPE,
                content = UploadContent.Bytes(signaturePng),
            )
        ) {
            PresignedUploadResult.Success -> Unit

            PresignedUploadResult.NetworkFailure -> {
                return SignatureUpdateResult.Failure(SignatureUpdateFailure.NETWORK_UNAVAILABLE)
            }

            PresignedUploadResult.InvalidUploadUrl -> {
                return SignatureUpdateResult.Failure(SignatureUpdateFailure.UNKNOWN)
            }

            PresignedUploadResult.Rejected -> {
                return SignatureUpdateResult.Failure(SignatureUpdateFailure.INVALID_SIGNATURE)
            }
        }

        val updatedSignature = when (
            val result = userDataSource.updateSignature(upload.uploadId)
        ) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return SignatureUpdateResult.Failure(result.error.toUpdateFailure())
        }

        return SignatureUpdateResult.Success(
            UserProfile(
                signatureUrl = updatedSignature.originalImageUrl,
                signatureThumbnailUrl = updatedSignature.originalImageUrl,
            ),
        )
    }

    private fun ApiError.toProfileLoadFailure(): UserProfileLoadFailure = when (this) {
        ApiError.Network -> UserProfileLoadFailure.NETWORK

        ApiError.InvalidResponse -> UserProfileLoadFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            401 -> UserProfileLoadFailure.UNAUTHORIZED
            403 -> UserProfileLoadFailure.FORBIDDEN
            else -> UserProfileLoadFailure.UNKNOWN
        }
    }

    private fun ApiError.toUpdateFailure(): SignatureUpdateFailure = when (this) {
        ApiError.Network -> SignatureUpdateFailure.NETWORK_UNAVAILABLE

        ApiError.InvalidResponse -> SignatureUpdateFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            400 -> SignatureUpdateFailure.INVALID_SIGNATURE
            401 -> SignatureUpdateFailure.REAUTHENTICATION_REQUIRED
            404 -> SignatureUpdateFailure.SIGNATURE_NOT_FOUND
            else -> SignatureUpdateFailure.UNKNOWN
        }
    }

    private companion object {
        const val MAX_SIGNATURE_BYTES = 1024 * 1024
        const val SIGNATURE_CONTENT_TYPE = "image/png"
    }
}
