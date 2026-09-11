package com.stonefive.chalkak.data.remote.user.model

import kotlinx.serialization.Serializable

@Serializable
data class SignatureUpdateRequest(val signatureOriginalUploadId: String)
