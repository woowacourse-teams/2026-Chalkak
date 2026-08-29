package com.stonefive.chalkak.data.remote.auth.model.response

import kotlinx.serialization.Serializable

@Serializable
data class SocialSignUpResponse(
    val userId: String,
    val accessToken: String? = null,
    val expiresIn: Long? = null,
)
