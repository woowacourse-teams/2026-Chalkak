package com.stonefive.chalkak.data.remote.auth.model.response

import kotlinx.serialization.Serializable

@Serializable
data class SignatureUploadResponse(
    val uploadId: String,
    val uploadUrl: String,
    val expiresInSeconds: Long,
    val signupToken: String,
    val signupTokenExpiresInSeconds: Long? = null,
)
