package com.stonefive.chalkak.data.remote.auth.model.request

import kotlinx.serialization.Serializable

@Serializable
data class SocialSignUpRequest(val signupToken: String)
