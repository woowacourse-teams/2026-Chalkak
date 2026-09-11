package com.stonefive.chalkak.data.remote.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignatureUpdateResponse(
    @SerialName("signatureOriginalImageUrl")
    val originalImageUrl: String,
)
