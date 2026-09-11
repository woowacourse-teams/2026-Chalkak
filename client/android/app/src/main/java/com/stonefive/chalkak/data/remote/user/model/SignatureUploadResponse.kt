package com.stonefive.chalkak.data.remote.user.model

import kotlinx.serialization.Serializable

@Serializable
data class SignatureUploadResponse(
    val uploadId: String,
    val uploadUrl: String,
    val expiresInSeconds: Long,
)
