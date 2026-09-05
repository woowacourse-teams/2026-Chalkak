package com.stonefive.chalkak.data.remote.auth.model.request

import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(val refreshToken: String)
