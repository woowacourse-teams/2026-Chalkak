package com.stonefive.chalkak.data.remote.auth.model.response

import kotlinx.serialization.Serializable

@Serializable
data class SocialLoginResponseDto(
    val status: String,
    val userId: String? = null,
    val accessToken: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresIn: Long? = null,
)
