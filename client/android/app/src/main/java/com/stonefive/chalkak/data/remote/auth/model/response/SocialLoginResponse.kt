package com.stonefive.chalkak.data.remote.auth.model.response

import kotlinx.serialization.Serializable

@Serializable
data class SocialLoginResponse(
    val status: String,
    val userId: String? = null,
)
