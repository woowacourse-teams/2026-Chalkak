package com.stonefive.chalkak.data.remote.auth.model.request

import kotlinx.serialization.Serializable

@Serializable
data class SignatureUploadRequest(
    val provider: String,
    val idToken: String,
)
