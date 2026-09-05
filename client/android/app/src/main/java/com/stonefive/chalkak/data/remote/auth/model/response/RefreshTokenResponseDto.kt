package com.stonefive.chalkak.data.remote.auth.model.response

import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenResponseDto(
    val accessToken: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresIn: Long? = null,
    val userId: String? = null,
)
