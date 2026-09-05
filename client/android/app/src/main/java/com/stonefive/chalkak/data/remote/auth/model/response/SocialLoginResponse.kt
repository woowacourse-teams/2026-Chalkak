package com.stonefive.chalkak.data.remote.auth.model.response

sealed interface SocialLoginResponse {
    data class LoginSuccess(
        val userId: String,
        val accessToken: String,
        val expiresIn: Long,
        val refreshToken: String,
        val refreshTokenExpiresIn: Long,
    ) : SocialLoginResponse

    data object SignUpRequired : SocialLoginResponse
}
